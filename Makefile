
VERSION = 0.0.4

.PHONY: all

all: runner tools

pipeline:
	docker build -f dockerfiles/pipeline.docker -t abdullin/tga-pipeline:base-latest . --no-cache
	docker tag abdullin/tga-pipeline:base-latest abdullin/tga-pipeline:base-${VERSION}

tools: pipeline
	docker build -f dockerfiles/tools.docker -t abdullin/tga-pipeline:tools-latest . --no-cache
	docker tag abdullin/tga-pipeline:tools-latest abdullin/tga-pipeline:tools-${VERSION}

runner: pipeline
	docker build -f dockerfiles/runner.docker -t abdullin/tga-pipeline:runner-latest . --no-cache
	docker tag abdullin/tga-pipeline:runner-latest abdullin/tga-pipeline:runner-${VERSION}

publish:
    docker push abdullin/tga-pipeline:base-latest
    docker push abdullin/tga-pipeline:base-${VERSION}

	docker push abdullin/tga-pipeline:tools-latest
	docker push abdullin/tga-pipeline:tools-${VERSION}

	docker push abdullin/tga-pipeline:runner-latest
	docker push abdullin/tga-pipeline:runner-${VERSION}
