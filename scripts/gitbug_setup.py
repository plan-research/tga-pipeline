#!/bin/python

import json
import os
import shutil
import subprocess

from cut_info import cut_map

if len(sys.argv) < 3:
	print("Usage: `./gutbug_setup.py *path to GitBug root* *path to output dir*`")
	sys.exit(1)

gitbug_data_dir = os.path.resolve(sys.argv[1], 'bugs/')
output_dir = sys.argv[2]

def clone(project_Json) -> str:
	if not os.path.exists(output_dir):
		os.makedirs(output_dir)
	os.chdir(output_dir)
	project_name = project_Json['repository'].split('/')[1]
	commit_id = project_Json['commit_hash']
	project_dir_name = "{}-{}".format(project_name, commit_id[0:10])
	project_dir = os.path.join(output_dir, project_dir_name)

	if os.path.exists(project_dir):
		return project_dir
	if not os.path.exists(project_dir):
		return None


	if not os.path.exists(project_dir):
		url = project_Json['clone_url']
		process = subprocess.Popen(['git', 'clone', url, project_dir_name], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		cloneOutput, cloneErr = process.communicate()
		if process.returncode != 0:
			print("Failed to clone url {}".format(url))
			print("Command error output: {}".format(cloneErr))
			shutil.rmtree(project_dir)
			return None

	os.chdir(project_dir)
	process = subprocess.Popen(['git', 'checkout', commit_id], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	checkoutOutput, checkoutErr = process.communicate()
	if process.returncode != 0:
		print("Failed to clone checkout commit {}".format(commit_id))
		print("Command error output: {}".format(checkoutErr))
		shutil.rmtree(project_dir)
		return None

	return project_dir

def build(project_json, project_dir):
	if os.path.exists(project_dir):
		return project_dir

	print(project_dir)
	os.chdir(project_dir)

	build_system = ''
	java_version = ''
	for file in os.listdir(project_dir):
		if file == 'pom.xml':
			build_system = 'maven'
		elif file == 'build.gradle.kts':
			build_system = 'gradle-kotlin'
		elif file == 'build.gradle':
			build_system = 'gradle-groovy'
	print(build_system)

	if build_system == 'maven':
		process = subprocess.Popen(['mvn', 'clean', 'package', '-Dmaven.test.skip=true'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		output, err = process.communicate()
		if process.returncode != 0:
			print("Failed to build maven project {}".format(project_dir))
			shutil.rmtree(project_dir)
			return

		process = subprocess.Popen(['mvn', 'dependency:copy-dependencies'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		output, err = process.communicate()
		if process.returncode != 0:
			print("Failed to copy maven dependencies {}".format(project_dir))

	elif build_system == 'gradle-kotlin' or build_system == 'gradle-groovy':
		process = subprocess.Popen(['./gradlew', 'build', '-x', 'test'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		output, err = process.communicate()
		if process.returncode != 0:
			print("Failed to build kotlin gradle project {}".format(project_dir))
			shutil.rmtree(project_dir)
			return


		build_file_path = ''
		build_file_patch = ''
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
				print("Failed to copy gradle dependencies {}".format(project_dir))


downloaded_projects = []

for file in os.listdir(gitbug_data_dir):
	project_Json = os.path.join(gitbug_data_dir, file)
	for json_Str in open(project_Json).read().removesuffix('\n').split('\n'):
		json_Str = json_Str.translate(str.maketrans({"\n": r"\\n"}))
		project_data = json.loads(json_Str)

		project_dir = clone(project_data)
		if project_dir == None:
			continue

		build(project_data, project_dir)
		downloaded_projects.append([project_data, project_dir])

benchmarks = []

for project_json, project_dir in downloaded_projects:
	benchmark = {}
	benchmark['name'] = project_json['repository'].split('/')[1]
	benchmark['root'] = project_dir
	benchmark['build_id'] = project_dir.split('/')[-1]

	class_path = []
	if os.path.exists(os.path.join(project_dir, 'target')):
		target_dir_path = os.path.join(project_dir, 'target')

		classes_dir_path = os.path.join(target_dir_path, 'classes')
		if os.path.exists(classes_dir_path):
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
		classes_dir_path = os.path.join(target_dir_path, 'classes')
		if os.path.exists(os.path.join(classes_dir_path, 'java')):
			classes_dir_path = os.path.join(classes_dir_path, 'java')
		if os.path.exists(os.path.join(classes_dir_path, 'kotlin')):
			classes_dir_path = os.path.join(classes_dir_path, 'kotlin')
		if os.path.exists(os.path.join(classes_dir_path, 'main')):
			classes_dir_path = os.path.join(classes_dir_path, 'main')

		class_path.append(classes_dir_path)

		dependency_dir = ''
		if os.path.exists(os.path.join(project_dir, 'dependencies')):
			dependency_dir = os.path.join(project_dir, 'dependencies')

		if os.path.exists(dependency_dir):
			for file in os.listdir(dependency_dir):
				class_path.append(os.path.join(dependency_dir, file))

	benchmark['classPath'] = class_path

	failed_tests = set({})
	for l in project_json['actions_runs']:
		tests = []
		if l is None:
			continue
		if type(l) is list:
			tests = l[0]['tests']
		else:
			tests = l['tests']

		for test in tests:
			if test['results'][0]['result'] == 'Failure':
				failed_tests.add(test['classname'].removesuffix('Test').removesuffix('Tests'))
	benchmark['klass'] = cut_map[benchmark['build_id']]

	# patched_class_name = project_json['bug_patch'].split('\n')[0].split(' ')[2].removeprefix('a/').removeprefix('src/').removeprefix('main/').removeprefix('java/').replace('/', '.').removesuffix('.java')

	# print(project_dir)
	# if len(failed_tests) == 1 and patched_class_name in failed_tests:
	# 	print("{} -> {}".format(benchmark['build_id'], patched_class_name))
	# print(patched_class_name)
	# print(failed_tests)
	# print()

	benchmarks.append(benchmark)

output_file_path = os.path.join(output_dir, 'benchmarks.json')
output_file = open(output_file_path, "w")
output_file.write(json.dumps(benchmarks, indent = 2))
output_file.close()
