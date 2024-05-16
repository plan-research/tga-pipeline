import subprocess
import tempfile

from generate_compose import KexArgs
from generate_compose import Tool
from generate_compose import generate_compose

# Global parameters
RUNNER_IMAGE = "abdullin/tga-pipeline:runner-latest"
TOOL_IMAGE = "abdullin/tga-pipeline:tools-latest"
BENCHMARKS_FILE = "/var/benchmarks/gitbug/benchmarks.json"

# Run-specific parameters
TOOL = Tool.kex
TOOL_ARGS = KexArgs({})
RUNS = 2
TIMEOUT = 2 * 60  # in seconds
THREADS = 2
RESULTS_DIR = "./results"


def main():
    compose_file = generate_compose(TOOL, TOOL_ARGS, RUNS, TIMEOUT, THREADS, RESULTS_DIR,
                                    RUNNER_IMAGE, TOOL_IMAGE, BENCHMARKS_FILE)

    tmp = tempfile.NamedTemporaryFile()
    with open(tmp.name, 'w') as file:
        print(file.name)
        print(compose_file)
        file.write(str(compose_file))

    subprocess.run(['docker', 'compose', '-f', tmp.name, 'up'])


if __name__ == '__main__':
    main()
