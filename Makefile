
.PHONY: all

all: runner tools

benchmarks:
	docker build -f benchmarks.docker -t abdullin/tga-benchmarks:latest .

tools: benchmarks
	docker build -f tools.docker -t abdullin/tga-tools:latest . --no-cache

runner: tools
	docker build -f runner.docker -t abdullin/tga-runner:latest . --no-cache
