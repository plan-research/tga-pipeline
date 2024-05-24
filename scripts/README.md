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
python3 ./scripts/execute_benchmark.py -h
usage: execute_benchmark.py [-h] --tool {Tool.kex,Tool.EvoSuite,Tool.TestSpark} --runName RUNNAME --runs {[0..100000]} --timeout TIMEOUT --workers WORKERS --output OUTPUT
                            [--kexOption KEXOPTION [KEXOPTION ...]] [--llm LLM] [--llmToken LLMTOKEN] [--spaceUser SPACEUSER] [--spaceToken SPACETOKEN] [--prompt PROMPT]

TGA pipeline executor

options:
  -h, --help            show this help message and exit
  --tool {Tool.kex,Tool.EvoSuite,Tool.TestSpark}
                        Name of tool
  --runName RUNNAME     Name of the experiment
  --runs {[0..100000]}  Number of total runs
  --timeout TIMEOUT     Timeout in seconds
  --workers WORKERS     Number of parallel workers
  --output OUTPUT       Path to folder with output
  --kexOption KEXOPTION [KEXOPTION ...]
                        Additional kex options, optional for kex
  --llm LLM             LLM to use, required for TestSpark
  --llmToken LLMTOKEN   Grazie token, required for TestSpark
  --spaceUser SPACEUSER
                        Space user name, required for TestSpark
  --spaceToken SPACETOKEN
                        Space token, required for TestSpark
  --prompt PROMPT       LLM prompt for test generation, optional for TestSpark
```

