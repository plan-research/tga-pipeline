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
