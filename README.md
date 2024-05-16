# tga-pipeline

Pipeline for automatic test generation assessment.

## Benchmarks

Currently, we use [GitBug](https://github.com/gitbugactions/gitbug-java) benchmarks.
`./scripts` directory provides scripts for downloading and building the benchmarks.

There is a Docker image containing pre-build benchmarks, it can be obtained using:

```shell
docker pull abdullin/tga-pipeline:benchmarks-latest 
# or a specific version, e.g. `benchmarks-0.0.6`
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
pipeline in default configuration is pretty simple:

```shell
./scripts/execute_benchmark.py
```

This script will generate a Compose file with default parameters and execute the pipeline.

## Configuration

All the configuration parameters are stored as global variables in [execute_benchmark.py](scripts/execute_benchmark.py)
script, so if you want to modify the default parameters (or, for example, choose a different tool), you will have to
manually edit the file. We are also planning to create a command line based alternative, but that is still in the works.

Pipeline has two different types of parameters: run specific and tool specific.

### Run specific parameters

These parameters determine the global execution configuration:

* `RUN_NAME` &mdash; Name of the current experiment;
* `RUNS` &mdash; Number of repeated executions of the benchmark;
* `TIMEOUT` &mdash; Timeout, is seconds, for each execution of test generation tool (i.e. it is a timeout for test
  generation for a single class);
* `THREADS` &mdash; Number of parallel executions that will be started; parallelization is currently performed on the
  benchmark level. E.g. if `THREADS = 2` and `RUNS = 10`, the pipeline will start two parallel pipelines, the first one
  will execute runs 0&ndash;4, the second one &mdash; 5&ndash;9;
* `RESULTS_DIR` &mdash; path to the folder where the pipeline will write all the execution results.

### Tools and tool specific parameters

Currently, the pipeline supports three tools: [Kex](https://github.com/vorpal-research/kex),
[EvoSuite](https://github.com/EvoSuite/evosuite) and [TestSpark](https://github.com/JetBrains-Research/TestSpark).

The pipeline expects two tool specific parameters:

* `TOOL` &mdash; an instance of enum class `Tool` that will determine the tool for test generation;
* `TOOL_ARGS` &mdash; an instance of class `ToolArgs` that will allow you to pass necessary or additional parameters to
  the selected tool.

To add support for the new tool, you need to:

* Add an implementation
  of [`TestGenerationTool`](tga-core/src/main/kotlin/org/plan/research/tga/core/tool/TestGenerationTool.kt) interface
  for your tool into the `tga-tool` project;
* Install your tool in the `tga-tools` Docker image by modifying [`tools.docker`](dockerfiles/tools.docker) dockerfile;
* Implement the necessary utility classes in [generate_compose.py](scripts/generate_compose.py) script.

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
