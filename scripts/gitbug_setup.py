#!/bin/python

import json
import logging
import os
import shutil
import subprocess
import sys

from cut_info import cut_map


def clone(output_dir, project_json) -> str:
	if not os.path.exists(output_dir):
		os.makedirs(output_dir)
	os.chdir(output_dir)
	project_name = project_json['repository'].split('/')[1]
	commit_id = project_json['commit_hash']
	project_dir_name = "{}-{}".format(project_name, commit_id[0:10])
	project_dir = os.path.join(output_dir, project_dir_name)

	if os.path.exists(project_dir):
		return project_dir

	logging.info("Cloning project '{}' from URL '{}'".format(project_dir_name, project_json['clone_url']))

	if not os.path.exists(project_dir):
		url = project_json['clone_url']
		process = subprocess.Popen(['git', 'clone', url, project_dir_name], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		cloneOutput, cloneErr = process.communicate()
		if process.returncode != 0:
			logging.error("Failed to clone url {}".format(url))
			logging.error("Executed command: {}".format(process.args))
			logging.error("Command error output: {}".format(cloneErr.decode("utf-8")))
			if os.path.exists(project_dir):
				shutil.rmtree(project_dir)
			return None

	os.chdir(project_dir)
	process = subprocess.Popen(['git', 'checkout', commit_id], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	checkoutOutput, checkoutErr = process.communicate()
	if process.returncode != 0:
		logging.error("Failed to clone checkout commit {}".format(commit_id))
		logging.error("Command error output: {}".format(checkoutErr.decode("utf-8")))
		shutil.rmtree(project_dir)
		return None

	return project_dir

def build(project_json, project_dir):
	if not os.path.exists(project_dir):
		return None

	logging.info("Building the project '{}'".format(project_dir))
	os.chdir(project_dir)

	build_system = ''
	for file in os.listdir(project_dir):
		if file == 'pom.xml':
			build_system = 'maven'
		elif file == 'build.gradle.kts':
			build_system = 'gradle-kotlin'
		elif file == 'build.gradle':
			build_system = 'gradle-groovy'
	logging.info("Detected build system: {}".format(build_system))

	if build_system == 'maven':
		process = subprocess.Popen(['mvn', 'clean', 'package', '-Dmaven.test.skip=true'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		output, err = process.communicate()
		if process.returncode != 0:
			logging.error("Failed to build maven project {}".format(project_dir))
			logging.error("Build command: {}".format(process.args))
			logging.error("Build error output: {}".format(err.decode("utf-8")))
			shutil.rmtree(project_dir)
			return None

		process = subprocess.Popen(['mvn', 'dependency:copy-dependencies'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		output, err = process.communicate()
		if process.returncode != 0:
			logging.error("Failed to copy maven dependencies {}".format(project_dir))
			shutil.rmtree(project_dir)
			return None

	elif build_system == 'gradle-kotlin' or build_system == 'gradle-groovy':
		process = subprocess.Popen(['./gradlew', 'build', '-x', 'test'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		output, err = process.communicate()
		if process.returncode != 0:
			logging.error("Failed to build kotlin gradle project {}".format(project_dir))
			logging.error("Build command: {}".format(process.args))
			logging.error("Build error output: {}".format(err.decode("utf-8")))
			shutil.rmtree(project_dir)
			return None

		if build_system == 'gradle-kotlin':
			build_file_path = os.path.join(project_dir, 'build.gradle.kts')
			build_file_patch = """
task("copyDependencies", Copy::class) {
    from(configurations.default).into("${layout.buildDirectory}/dependencies")
}
	"""
		else:
			build_file_path = os.path.join(project_dir, 'build.gradle')
			build_file_patch = """
task copyDependencies(type: Copy) {
  from configurations.default
  into 'dependencies'
}
	"""

		process = subprocess.Popen(['./gradlew', 'copyDependencies'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		output, err = process.communicate()
		if process.returncode != 0:
			build_file = open(build_file_path, "a")
			build_file.write(build_file_patch)
			build_file.close()

			process = subprocess.Popen(['./gradlew', 'copyDependencies'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
			output, err = process.communicate()
			if process.returncode != 0:
				logging.error("Failed to copy gradle dependencies {}".format(project_dir))
				logging.error("Build command: {}".format(process.args))
				logging.error("Build error output: {}".format(err.decode("utf-8")))
				shutil.rmtree(project_dir)
				return None

	return project_dir


def download_projects(gitbug_data_dir, output_dir):
	downloaded_projects = []
	for file in os.listdir(gitbug_data_dir):
		project_json = os.path.join(gitbug_data_dir, file)
		for json_Str in open(project_json).read().removesuffix('\n').split('\n'):
			json_Str = json_Str.translate(str.maketrans({"\n": r"\\n"}))
			project_data = json.loads(json_Str)

			project_dir = clone(output_dir, project_data)
			if project_dir == None:
				continue

			project_dir = build(project_data, project_dir)
			if project_dir == None:
				continue

			downloaded_projects.append([project_data, project_dir])
	return downloaded_projects


def produce_benchmarks(downloaded_projects):
	benchmarks = []
	for project_json, project_dir in downloaded_projects:
		benchmark = {}
		benchmark['name'] = project_json['repository'].split('/')[1]
		benchmark['root'] = project_dir
		benchmark['build_id'] = project_dir.split('/')[-1]

		src_path = ''
		bin_path = ''
		class_path = []

		for word in ['src', 'main', 'java']:
			if os.path.exists(os.path.join(project_dir, word)):
				src_path = os.path.join(project_dir, word)

		if not src_path:
			logging.error("Could not find src path of project {}".format(project_dir))
			continue

		if os.path.exists(os.path.join(project_dir, 'target')):
			target_dir_path = os.path.join(project_dir, 'target')

			classes_dir_path = os.path.join(target_dir_path, 'classes')
			if os.path.exists(classes_dir_path):
				bin_path = classes_dir_path
				class_path.append(classes_dir_path)

			dependency_dir = ''
			if os.path.exists(os.path.join(target_dir_path, 'dependency')):
				dependency_dir = os.path.join(target_dir_path, 'dependency')
			elif os.path.exists(os.path.join(target_dir_path, 'dependencies')):
				dependency_dir = os.path.join(target_dir_path, 'dependencies')
			elif os.path.exists(os.path.join(target_dir_path, 'lib')):
				dependency_dir = os.path.join(target_dir_path, 'lib')

			if os.path.exists(dependency_dir):
				for file in os.listdir(dependency_dir):
					class_path.append(os.path.join(dependency_dir, file))
		if os.path.exists(os.path.join(project_dir, 'build')):
			build_dir_path = os.path.join(project_dir, 'build')
			classes_dir_path = os.path.join(build_dir_path, 'classes')
			if os.path.exists(os.path.join(classes_dir_path, 'java')):
				classes_dir_path = os.path.join(classes_dir_path, 'java')
			if os.path.exists(os.path.join(classes_dir_path, 'kotlin')):
				classes_dir_path = os.path.join(classes_dir_path, 'kotlin')
			if os.path.exists(os.path.join(classes_dir_path, 'main')):
				classes_dir_path = os.path.join(classes_dir_path, 'main')
			bin_path = classes_dir_path

			class_path.append(classes_dir_path)

			dependency_dir = ''
			if os.path.exists(os.path.join(project_dir, 'dependencies')):
				dependency_dir = os.path.join(project_dir, 'dependencies')

			if os.path.exists(dependency_dir):
				for file in os.listdir(dependency_dir):
					if file.endswith('.zip'):
						continue
					class_path.append(os.path.join(dependency_dir, file))

		benchmark['src'] = src_path
		benchmark['bin'] = bin_path
		benchmark['classPath'] = class_path
		benchmark['klass'] = cut_map[benchmark['build_id']]

		benchmarks.append(benchmark)
	return benchmarks

def main():
	logging.basicConfig(
		handlers=[
			logging.FileHandler("gitbug_setup.log"),
			logging.StreamHandler()
		],
		format='[%(asctime)s][%(levelname)s] %(message)s',
		level=logging.INFO
	)

	if len(sys.argv) < 3:
		logging.error("Usage: `./gutbug_setup.py *path to GitBug root* *path to output dir*`")
		sys.exit(1)

	gitbug_data_dir = os.path.join(sys.argv[1], 'data/bugs/')
	output_dir = sys.argv[2]

	downloaded_projects = download_projects(gitbug_data_dir, output_dir)
	benchmarks = produce_benchmarks(downloaded_projects)

	output_file_path = os.path.join(output_dir, 'benchmarks.json')
	output_file = open(output_file_path, "w")
	benchmarksStr = json.dumps(benchmarks, indent = 2).replace(output_dir.removesuffix('/'), '/var/benchmarks')
	output_file.write(benchmarksStr)
	output_file.close()


if __name__ == '__main__':
	main()
