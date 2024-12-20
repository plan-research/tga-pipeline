# tga-pipeline

Pipeline for automatic test generation assessment.

## Benchmarks

Currently, we use [GitBug](https://github.com/gitbugactions/gitbug-java) benchmarks.
`./scripts` directory provides scripts for downloading and building the benchmarks.

There is a Docker image containing pre-build benchmarks, it can be obtained using:

```shell
docker pull abdullin/tga-pipeline:benchmarks-latest 
# or a specific version, e.g. `benchmarks-0.0.41`
```

One can also build that Docker image locally if necessary:

```shell
docker build -f dockerfiles/benchmarks.docker -t tga-pipeline:benchmarks-latest . 
```

Note: Docker image uses OpenJDK 11.
Some of the GitBug projects require specific Java
versions and may fail, it is an expected behavior.
In total, the scripts should produce 160 benchmarks.

## Build and run

The Pipeline consists of several Docker images that run via Docker Compose.

Docker images are build via simple make command:

```bash
make all
```

However, all the images are also available through [Docker Hub](https://hub.docker.com/r/abdullin/tga-pipeline/tags).

Pipeline has a log of configuration parameters, therefore manually writing and configuring Compose file may be
challenging. That is why we provide a set of Python scripts for pipeline configuration and execution. Running the
pipeline is pretty simple:

```shell
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

Example:

```shell
python3 ./scripts/execute_benchmark.py --tool kex --runName test --runs 10 --workers 1 --timeout 120 --output /home/kex-results
```

The script will generate a Compose file with specified parameters and execute the pipeline.

## Configuration

All the configuration parameters are stored as global variables in [execute_benchmark.py](scripts/execute_benchmark.py)
script, so if you want to modify the default parameters (or, for example, choose a different tool), you will have to
manually edit the file. We are also planning to create a command line based alternative, but that is still in the works.

Pipeline has two different types of parameters: run specific and tool specific.

### Run specific parameters

These parameters determine the global execution configuration:

* `tool` &mdash; an instance of enum class `Tool` that will determine the tool for test generation
* `runName` &mdash; Name of the current experiment;
* `runs` &mdash; Number of repeated executions of the benchmark;
* `timeout` &mdash; Timeout, is seconds, for each execution of test generation tool (i.e. it is a timeout for test
  generation for a single class);
* `workers` &mdash; Number of parallel workers that will be started; parallelization is currently performed on the
  benchmark level. E.g. if `workers = 2` and `runs = 10`, the pipeline will start two parallel workers, the first one
  will execute runs 0&ndash;4, the second one &mdash; 5&ndash;9;
* `output` &mdash; path to the folder where the pipeline will write all the execution results.

### Tools and tool-specific parameters

Currently, the pipeline supports three tools: [Kex](https://github.com/vorpal-research/kex),
[EvoSuite](https://github.com/EvoSuite/evosuite) and [TestSpark](https://github.com/JetBrains-Research/TestSpark).

Depending on the chosen tool, the pipeline expects different sets of tool-specific parameters.

#### Kex

[Kex](https://github.com/vorpal-research/kex) is an automatic test generation tool based on symbolic execution.
By default, currently we use concolic mode of Kex. And by default, Kex does not require any additional parameters.
However, you can change the Kex behavior by providing map of additional options in `KexArgs` class.

#### TestSpark

[TestSpark](https://github.com/JetBrains-Research/TestSpark) is an IntelliJ IDEA plugin for generating unit tests.
TestSpark natively integrates different test generation tools and techniques in the IDE.
Currently, we use TestSpark for LLM-based test generation by running it in the headless mode of IDEA.
TestSpark requires you to provide four necessary arguments in the `TestSparkArgs` class:

* Name of the LLM to use;
* Grazie token for accessing the LLM;
* Space username;
* Space token (these two are required for running TestSpark in headless mode).

Additionally, you can provide a custom prompt (as a string) for test generation. By default, the pipeline uses prompt
provided by TestSpark developers.

### EvoSuite

[EvoSuite](https://github.com/EvoSuite/evosuite) automatically generates JUnit test suites for Java classes,
targeting code coverage criteria such as branch coverage.
It uses an evolutionary approach based on a genetic algorithm to derive test suites.
To improve readability, the generated unit tests are minimized,
and regression assertions that capture the current behavior of the tested classes are added to the tests.

Currently, the pipeline uses a pre-build version of [EvoSuite-1.0.5](lib/evosuite-1.0.5.jar),
which forces us to use JDK 11 for the experiments, as it fails on the newer versions.

Running EvoSuite does not require any additional arguments, one can just pass an empty instance of `EvoSuiteArgs`.

## Extending the pipeline

To add support for the new tool, you need to:

* Add an implementation
  of [`TestGenerationTool`](tga-core/src/main/kotlin/org/plan/research/tga/core/tool/TestGenerationTool.kt) interface
  for your tool into the `tga-tool` project;
* Install your tool in the `tga-tools` Docker image by modifying [`tools.docker`](dockerfiles/tools.docker) dockerfile;
* Implement the necessary utility classes in [generate_compose.py](scripts/generate_compose.py) script;
* Add the necessary tool-specific parameters in [execute_benchmark.py](./scripts/execute_benchmark.py) script.
