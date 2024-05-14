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

To start the pipeline, one can run the following command:

```bash
docker compose --env-file tool-configs/kex-config.env up
```

## Configuration

The pipeline configuration is done via the Compose environment file
(e.g. [kex-config.env](tool-configs/kex-config.env)), where
you can specify arguments for each of the Docker containers being executed.

The pipeline currently consists of two containers: *runner* and *tools*.

### Runner

*Runner* is a container that runs the server part of the pipeline.
It controls the whole execution: running the tool, computing coverage, etc.
Current configuration options include:

```
 -c,--config <arg>    configuration file
 -h,--help            print this help and quit
 -n,--runs <arg>      number of runs
 -o,--output <arg>    output directory
 -p,--port <arg>      server port to run on
 -t,--timeout <arg>   time limit for test generation, in seconds
 ```

### Tools

*Tools* is a container that controls the test generation tool and controls the interactions
between a tool and a *runner*.
Currently, the pipeline supports three tools: [Kex](https://github.com/vorpal-research/kex),
[EvoSuite](https://github.com/EvoSuite/evosuite) and [TestSpark](https://github.com/JetBrains-Research/TestSpark).

You can choose between these tools by providing different [compose environment files](tool-configs).
In general, each environment file should contain definitions of three variables:

```shell
TGA_OUTPUT_DIR=*path* # local path to store the execution results
TGA_TOOL_NAME=*name* # name of the tool to be used in generation "kex", "EvoSuite" or "TestSpark"
TGA_TOOL_ARGS= # arguments that are specific for each tool, may be empty (e.g. for EvoSuite and Kex)
```

Note that each tool may require you to also provide additional arguments and/or environment variables.
The requirements of each supported tool are described further.

To add support for the new tool, one is required to:

* Add an implementation
  of [`TestGenerationTool`](tga-core/src/main/kotlin/org/plan/research/tga/core/tool/TestGenerationTool.kt) interface
  for your tool into the `tga-tool` project
* Install your tool in the `tga-tools` Docker image by modifying [`tools.docker`](dockerfiles/tools.docker) dockerfile

#### Kex

[Kex](https://github.com/vorpal-research/kex) is an automatic test generation tool based on symbolic execution.
By default, currently we use concolic mode of Kex.
However, you may change that by providing additional arguments to Kex via the `--toolArgs` option.
Otherwise, Kex does not require any additional arguments to be passed.

Additionally, if you are trying to run the pipeline outside of Docker images, Kex also requires you
to specify `KEX_HOME` environment variable with the path to Kex installation.

#### TestSpark

[TestSpark](https://github.com/JetBrains-Research/TestSpark) is an IntelliJ IDEA plugin for generating unit tests.
TestSpark natively integrates different test generation tools and techniques in the IDE.
Currently, we use TestSpark for LLM-based test generation by running it in the headless mode of IDEA.
TestSpark requires you to provide additional arguments:

```
 -h,--help               print this help and quit
    --llm <arg>          llm for test generation
    --llmToken <arg>     token for LLM access
    --prompt <arg>       prompt to use for test generation
    --spaceToken <arg>   token for accessing Space
    --spaceUser <arg>    Space user name
```

Additionally, if you are trying to run the pipeline outside of Docker images, TestSpark also requires you
to specify `TEST_SPARK_HOME` environment variable with the path to TestSpark installation.

### EvoSuite

[EvoSuite](https://github.com/EvoSuite/evosuite) automatically generates JUnit test suites for Java classes,
targeting code coverage criteria such as branch coverage.
It uses an evolutionary approach based on a genetic algorithm to derive test suites.
To improve readability, the generated unit tests are minimized,
and regression assertions that capture the current behavior of the tested classes are added to the tests.

Currently, the pipeline uses a pre-build version of [EvoSuite-1.0.5](lib/evosuite-1.0.5.jar),
which forces us to use JDK 11 for the experiments, as it fails on the newer versions.

Running EvoSuite does not require any additional arguments, one can just use
the provided [evosuite-config.env](tool-configs/evosuite-config.env) environment file.
