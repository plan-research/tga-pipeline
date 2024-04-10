
.PHONY: all

all: runner tools

benchmarks:
	docker build -f dockerfiles/benchmarks.docker -t abdullin/tga-benchmarks:latest .

pipeline: benchmarks
	docker build -f dockerfiles/pipeline.docker -t abdullin/tga-pipeline:latest . --no-cache

tools: pipeline
	docker build -f dockerfiles/tools.docker -t abdullin/tga-tools:latest . --no-cache

runner: pipeline
	docker build -f dockerfiles/runner.docker -t abdullin/tga-runner:latest . --no-cache
