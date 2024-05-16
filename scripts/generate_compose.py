from collections.abc import Iterable
from enum import Enum


class Tool(Enum):
    kex = 1
    EvoSuite = 2
    TestSpark = 3


class ToolArgs:
    pass


class KexArgs(ToolArgs):
    def __init__(self, options: dict[str, str]):
        self.options = options

    def __str__(self):
        return ' '.join(f'--option {option}:{self.options[option]}' for option in self.options)


class EvoSuiteArgs(ToolArgs):
    def __init__(self):
        pass

    def __str__(self):
        return ''


class TestSparkArgs(ToolArgs):
    def __init__(self, model_name: str, model_token: str, space_user: str, space_token: str, prompt: str = None):
        self.model_name = model_name
        self.model_token = model_token
        self.space_user = space_user
        self.space_token = space_token
        self.prompt = prompt

    def __str__(self):
        cmd = (f'--llm {self.model_name} '
               f'--llmToken {self.model_token} '
               f'--spaceUser {self.space_user} '
               f'--spaceToken {self.space_token}')
        if self.prompt is not None:
            cmd += f' --prompt \"{self.prompt}\"'

        return cmd


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


def generate_compose(
        tool: Tool,
        tool_args: ToolArgs,
        run_name: str,
        runs: int,
        timeout: int,
        threads: int,
        results_path: str,
        runner_image: str,
        tool_image: str,
        benchmarks_path: str
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

        network = Network(f'network-{tool.name}-{thread}')
        result.add_network(network)

        runner_service = Service(
            name=f'runner-{tool.name}-{thread}',
            image=runner_image,
            command=f'--args="-p 10000 -c {benchmarks_path} -t {timeout} -o /var/results '
                    f'--runName {run_name} --runs {starting_run}..{starting_run + thread_runs - 1}"'
        )
        runner_service.add_network(network)
        runner_service.add_volume(result_volume, '/var/results')

        tool_service = Service(
            name=f'tool-{tool.name}-{thread}',
            image=tool_image,
            command=f'--args="--ip {runner_service.name} --port 10000 --tool {tool.name} --toolArgs=\'{tool_args}\'"'
        )
        tool_service.add_network(network)
        tool_service.add_volume(result_volume, '/var/results')

        result.add_service(runner_service)
        result.add_service(tool_service)
        starting_run += thread_runs

    return result
