
.PHONY: all

all: runner tool

benchmarks:
	docker build -f benchmarks.docker -t abdullin/tga-benchmarks:latest .

runner: benchmarks
	docker build -f runner.docker -t abdullin/tga-runner:latest . --no-cache

tool: benchmarks
	docker build -f tool.docker -t abdullin/tga-tool:latest . --no-cache
