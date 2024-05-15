#!/bin/python

import argparse
from collections.abc import Iterable

RUNNER_IMAGE = "abdullin/tga-pipeline:runner-latest"
TOOL_IMAGE = "abdullin/tga-pipeline:tools-latest"

DOCKER_BENCHMARKS = "/var/benchmarks/gitbug/benchmarks.json"
DOCKER_RESULTS_DIR = "/var/results"


def make_indent(indentation: int) -> str:
    return " " * indentation


class Volume:
    def __init__(self, name: str, is_external: bool) -> None:
        self.name = name
        self.is_external = is_external

    def print(self, indent: int = 2) -> str:
        return f'{indent} - {self.name}:'

    def __str__(self):
        return self.print()


class Network:
    def __init__(self, name: str, driver: str = 'bridge'):
        self.name = name
        self.driver = driver

    def print(self, indent: int = 2) -> str:
        return '\n'.join([
            f'{make_indent(indent)}{self.name}:',
            f'{make_indent(indent * 2)}driver: {self.driver}',
        ])

    def __str__(self):
        return self.print()


class Service:
    def __init__(self, name: str, image: str, command: str):
        self.name = name
        self.image = image
        self.command = command
        self.networks = []
        self.volumes = {}

    def print(self, indent: int = 2) -> str:
        return '\n'.join([
            f'{make_indent(indent)}{self.name}:',
            f'{make_indent(indent * 2)}image: {self.image}',
            f'{make_indent(indent * 2)}command: {self.command}',
            f'{make_indent(indent * 2)}networks:',
            '\n'.join([f'{make_indent(indent * 3)}- {network.name}' for network in self.networks]),
            f'{make_indent(indent * 2)}volumes:',
            '\n'.join([f'{make_indent(indent * 3)}- {volume.name}:{self.volumes[volume]}' for volume in
                       self.volumes]),
        ])

    def add_network(self, network: Network) -> None:
        self.networks.append(network)

    def add_volume(self, volume: Volume, mounting_point: str) -> None:
        self.volumes[volume] = mounting_point

    def __str__(self):
        return self.print()


class ComposeFile:
    def __init__(self):
        self.services = []
        self.networks = set()
        self.volumes = set()

    def add_service(self, service: Service):
        self.services.append(service)

    def add_network(self, network: Network):
        self.networks.add(network)

    def add_volume(self, volume: Volume):
        if volume.is_external:
            self.volumes.add(volume)

    def print(self, indent: int = 2) -> str:
        services_str = '\nservices:\n' + '\n'.join([service.print(indent) for service in self.services])
        networks_str = '\nnetworks:\n' + '\n'.join([network.print(indent) for network in self.networks])
        volumes_str = '\nvolumes:\n' + '\n'.join([volume.print(indent) for volume in self.volumes])
        if len(self.services) == 0:
            services_str = ''
        if len(self.networks) == 0:
            networks_str = ''
        if len(self.volumes) == 0:
            volumes_str = ''
        return services_str + networks_str + volumes_str

    def __str__(self):
        return self.print()


class IntRange(Iterable[int]):
    def __init__(self, start: int, end: int):
        self.start = start
        self.end = end

    def __contains__(self, num: int) -> bool:
        return self.start <= num <= self.end

    def __str__(self):
        return f'[{self.start}..{self.end}]'

    def __iter__(self):
        return iter([f'[{self.start}..{self.end}]'])


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog='generate_compose.py',
        description='Generate a compose file for running tga-pipeline experiment',
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        '-o', '--output', type=str,
        default='compose.yaml',
        required=False,
        help='Path to write the compose file',
    )
    parser.add_argument(
        '--tool', type=str, choices=['kex', 'EvoSuite', 'TestSpark'],
        required=True,
        help='The tool to run'
    )
    parser.add_argument(
        '--runs', type=int, choices=IntRange(1, 100),
        required=True,
        help='Number of runs'
    )
    parser.add_argument(
        '--timeout', type=int, choices=IntRange(1, 100_000),
        default=120,
        required=False,
        help='Timeout for each benchmark, in seconds'
    )
    parser.add_argument(
        '--threads', type=int, choices=IntRange(1, 100),
        default=1,
        required=False,
        help='Number of parallel threads'
    )
    parser.add_argument(
        '--results', type=str,
        required=True,
        help='Path to directory to write results'
    )
    return parser.parse_args()


def generate_compose(
        tool_name: str,
        runs: int,
        timeout: int,
        threads: int,
        results_path: str,
) -> ComposeFile:
    result = ComposeFile()

    runs_per_thread = runs // threads
    leftover = runs % threads
    starting_run = 0

    result_volume = Volume(results_path, is_external=False)
    result.add_volume(result_volume)

    for thread in range(threads):
        thread_runs = runs_per_thread
        if leftover > 0:
            thread_runs += 1
            leftover -= 1

        network = Network(f'network-{tool_name}-{thread}')
        result.add_network(network)

        runner_service = Service(
            name=f'runner-{tool_name}-{thread}',
            image=RUNNER_IMAGE,
            command=f'--args="-p 10000 -c {DOCKER_BENCHMARKS} -t {timeout} -o {DOCKER_RESULTS_DIR} '
                    f'--runs {starting_run}..{starting_run + thread_runs - 1}"'
        )
        runner_service.add_network(network)
        runner_service.add_volume(result_volume, DOCKER_RESULTS_DIR)

        tool_service = Service(
            name=f'tool-{tool_name}-{thread}',
            image=TOOL_IMAGE,
            command=f'--args="--ip {runner_service.name} --port 10000 --tool {tool_name} --toolArgs=''"'
        )
        tool_service.add_network(network)
        tool_service.add_volume(result_volume, DOCKER_RESULTS_DIR)

        result.add_service(runner_service)
        result.add_service(tool_service)
        starting_run += thread_runs

    return result


def main():
    args = parse_arguments()

    output_path = args.output
    tool_name = args.tool
    runs = args.runs
    timeout = args.timeout
    threads = args.threads
    results_path = args.results

    compose_file = generate_compose(tool_name, runs, timeout, threads, results_path)
    with open(output_path, 'w') as file:
        print(compose_file)
        file.write(str(compose_file))


if __name__ == '__main__':
    main()
