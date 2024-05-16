# TGA pipeline scripts

A set of utility scripts for [tga-pipeline](..) that help to build the dataset and to run the experiments.

## Building the dataset

[Gitbug_setup.py](gitbug_setup.py) is a script for building test generation assessment benchmarks
from [GitBug](https://github.com/gitbugactions/gitbug-java)
benchmarks.

### Usage

```bash
./scripts/gitbug_setup.py *path to GitBug reposotory root dir* *path to output dir*
```

## Running the experiments

[Execute_benchmark.py](execute_benchmark.py) is a script for running the experiments on the dataset.
It generates a Docker Compose file with the defined configuration and runs in.
Currently, configuration is defined inside the [execute_benchmark.py](execute_benchmark.py) script as
a set of global variables.
There are plans in the future to make a command-line-based configuration possible.

The script automatically generates Docker Compose configuration using [generate_compose.py](generate_compose.py).
All the necessary implementation details can be found there.

### Usage

```bash
./scripts/execute_benchmark.py
```

