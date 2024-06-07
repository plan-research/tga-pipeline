
VERSION = 0.0.9

.PHONY: all publish

all: runner tools

benchmarks:
	docker build -f dockerfiles/benchmarks.docker -t abdullin/tga-pipeline:benchmarks-latest .
	docker tag abdullin/tga-pipeline:benchmarks-latest abdullin/tga-pipeline:benchmarks-${VERSION}

	docker push abdullin/tga-pipeline:benchmarks-latest
	docker push abdullin/tga-pipeline:benchmarks-${VERSION}

pipeline:
	docker build -f dockerfiles/pipeline.docker -t abdullin/tga-pipeline:base-latest .
	docker tag abdullin/tga-pipeline:base-latest abdullin/tga-pipeline:base-${VERSION}

tools: pipeline
	docker build -f dockerfiles/tools.docker -t abdullin/tga-pipeline:tools-latest .
	docker tag abdullin/tga-pipeline:tools-latest abdullin/tga-pipeline:tools-${VERSION}

runner: pipeline
	docker build -f dockerfiles/runner.docker -t abdullin/tga-pipeline:runner-latest .
	docker tag abdullin/tga-pipeline:runner-latest abdullin/tga-pipeline:runner-${VERSION}

publish: pipeline runner tools
	docker push abdullin/tga-pipeline:base-latest
	docker push abdullin/tga-pipeline:base-${VERSION}
	docker push abdullin/tga-pipeline:tools-latest
	docker push abdullin/tga-pipeline:tools-${VERSION}
	docker push abdullin/tga-pipeline:runner-latest
	docker push abdullin/tga-pipeline:runner-${VERSION}
