
.PHONY: all

all: runner tools

benchmarks:
	docker build -f dockerfiles/benchmarks.docker -t abdullin/tga-benchmarks:latest .

tools: benchmarks
	docker build -f dockerfiles/tools.docker -t abdullin/tga-tools:latest . --no-cache

runner: tools
	docker build -f dockerfiles/runner.docker -t abdullin/tga-runner:latest . --no-cache
