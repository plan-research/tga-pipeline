# tga-pipeline

Pipeline for automatic test generation assessment.

## Benchmarks

Currently, we use [GitBug](https://github.com/gitbugactions/gitbug-java) benchmarks.
`./scripts` directory provides scripts for downloading and building the benchmarks.

There is also a docker image for building benchmarks, which can be run using
```shell
docker build -f benchmarks.docker -t tga-benchmarks . 
```

Note: docker image uses OpenJDK 17. Some of the GitBug projects require specific Java
versions and may fail, it is an expected behaviour. In total, the scripts should produce
176 benchmarks.

## Build and run

Pipeline consists of several docker images that run via docker compose. 

Docker images are build via simple make command:
```bash
make all
```

To start the pipeline one can run the following command:

```bash
docker compose --env-file pipeline-config.env up
```

## Configuration

The pipeline configuration is done via the [pipeline-config.env](pipeline-config.env), where
you can specify arguments for each of the docker containers being executed.

The pipeline currently consists of two containers: *runner* and *tools*.

### Runner 
*Runner* is a container that runs the server part of the pipeline.
It controls 
the whole execution: running the tool, computing metrics, computing coverage, etc.
Current configuration options are:
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
Currently, the pipeline supports two tools: [Kex](https://github.com/vorpal-research/kex)
and [TestSpark](https://github.com/JetBrains-Research/TestSpark).

You can choose between these tools by modifying parameters for *tools* container in the [pipeline-config.env](pipeline-config.env):
```
 -h,--help             print this help and quit
 -i,--ip <arg>         tga server address
 -p,--port <arg>       tga server port
 -t,--tool <arg>       tool name
    --toolArgs <arg>   additional tool arguments
```

Note that each tool may require you to also provide additional arguments and/or environment variables. The requirements of
each supported tool are described further.

To add support of the new tool one is required to:
* Add an implementation of [`TestGenerationTool`](tga-core/src/main/kotlin/org/plan/research/tga/core/tool/TestGenerationTool.kt) interface
for your tool into the `tga-tool` project
* Install your tool in the `tga-tools` docker image by modifying [`tools.docker`](dockerfiles/tools.docker) dockerfile

#### Kex

[Kex](https://github.com/vorpal-research/kex) is an automatic test generation tool based on symbolic execution.
By default, currently we use concolic mode of Kex.
However, you may change that by providing additional arguments to Kex via the `--toolArgs` option. 
Otherwise, Kex does not require any additional arguments to be passed.

Additionally, if you are trying to run the pipeline outside of docker images, Kex also requires you
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

Additionally, if you are trying to run the pipeline outside of docker images, TestSpark also requires you
to specify `TEST_SPARK_HOME` environment variable with the path to TestSpark installation.
