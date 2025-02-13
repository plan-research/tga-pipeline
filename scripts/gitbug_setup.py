#!/bin/python3

import json
import logging
import os
import shutil
import subprocess
import sys
import tempfile
import errno
import stat
import time

from cut_info import cut_map


GRADLEW_CMD = '.\gradlew.bat' if sys.platform.startswith('win') else './gradlew'
MVN_CMD = 'mvn.cmd' if sys.platform.startswith('win') else 'mvn'

def handle_remove_readonly(func, path, exc):
	"""
	On some Windows machines, for example, mine, shutil.rmtree() fails with PermissionError.
	This PermissionError seems to be caused because of missing permissions to delete a file
	and the locking placed on the recently created file as a process still uses it.
	To complete the execution of shutil.rmtree(), we need to give the process the necessary rights and
	wait a bit longer for the lock on the resource to disappear.
	This function checks which function threw the error, gives correct permissions, and
	retries to execute the failed command up to five times with an increasing delay each time.

	:param func: Function which threw an error and should be handled by this function.
	:param path: Path on which the failed function was called.
	:param exc: Returned exception information.
	:return:
	"""
	excvalue = exc[1]
	if func in (os.rmdir, os.remove, os.unlink) and excvalue.errno == errno.EACCES:
		os.chmod(path, stat.S_IRWXU| stat.S_IRWXG| stat.S_IRWXO) # 0777
		time.sleep(5)
		try:
			func(path)
		except PermissionError as e:
			if e.errno != errno.EACCES:
				# Retry mechanism
				for i in [10,15,20,25,30]:  # Retry up to 5 times
					try:
						time.sleep(i)  # Wait for i seconds before retrying
						func(path)
						break
					except PermissionError:
						continue
				else:
					raise

	else:
		raise

def clone(output_dir, project_json) -> str:
	if not os.path.exists(output_dir):
		os.makedirs(output_dir)
	os.chdir(output_dir)
	project_name = project_json['repository'].split('/')[1]
	commit_id = project_json['previous_commit_hash']
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
				shutil.rmtree(project_dir, onerror=handle_remove_readonly)
			return None

	os.chdir(project_dir)
	process = subprocess.Popen(['git', 'checkout', commit_id], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	checkoutOutput, checkoutErr = process.communicate()
	if process.returncode != 0:
		logging.error("Failed to clone checkout commit {}".format(commit_id))
		logging.error("Command error output: {}".format(checkoutErr.decode("utf-8")))
		shutil.rmtree(project_dir, onerror=handle_remove_readonly)
		return None

	return project_dir

def build(project_json, project_dir, apply_patch):
	if not os.path.exists(project_dir):
		return None

	logging.info("Building the project '{}'".format(project_dir))
	os.chdir(project_dir)

	if apply_patch:
		logging.info("Applying bug patch")
		patch = project_json['bug_patch']

		tmp = tempfile.NamedTemporaryFile()
		with open(tmp.name, 'w') as f:
			f.write(patch)

		logging.info(f'Patch temporarily written to {tmp.name}')
		p = subprocess.Popen(['git', 'apply', tmp.name])
		_, _ = p.communicate()
		if p.returncode == 0:
			logging.info('Patch successfully applied')
		else:
			logging.error('Failed to successfully apply patch')

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
		try:
			process = subprocess.Popen([MVN_CMD, 'clean', 'package', '-DskipTests'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		except FileNotFoundError:
			logging.error("Failed to find Maven executable at location '{}'. Check your Maven PATH variable.".format(MVN_CMD))
			shutil.rmtree(project_dir, onerror=handle_remove_readonly)
			raise
		output, err = process.communicate()
		if process.returncode != 0:
			logging.error("Failed to build maven project {}".format(project_dir))
			logging.error("Build command: {}".format(process.args))
			logging.error("Build error output: {}".format(err.decode("utf-8")))
			if err == b"":
				logging.error("Build process output: {}".format(output.decode("utf-8")))
			shutil.rmtree(project_dir, onerror=handle_remove_readonly)
			return None

		process = subprocess.Popen([MVN_CMD, 'dependency:copy-dependencies'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		_, _ = process.communicate()
		if process.returncode != 0:
			logging.error("Failed to copy maven dependencies {}".format(project_dir))
			shutil.rmtree(project_dir, onerror=handle_remove_readonly)
			return None

	elif build_system == 'gradle-kotlin' or build_system == 'gradle-groovy':
		try:
			process = subprocess.Popen([GRADLEW_CMD, 'build', '-x', 'test'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		except FileNotFoundError:
			logging.error("Failed to find Gradlew executable at location '{}'.".format(GRADLEW_CMD))
			shutil.rmtree(project_dir, onerror=handle_remove_readonly)
			raise
		output, err = process.communicate()
		if process.returncode != 0:
			logging.error("Failed to build kotlin gradle project {}".format(project_dir))
			logging.error("Build command: {}".format(process.args))
			logging.error("Build error output: {}".format(err.decode("utf-8", errors="replace")))
			if err == b"":
				logging.error("Build process output: {}".format(output.decode("utf-8")))
			shutil.rmtree(project_dir, onerror=handle_remove_readonly)
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

		process = subprocess.Popen([GRADLEW_CMD, 'copyDependencies'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
		output, err = process.communicate()
		if process.returncode != 0:
			build_file = open(build_file_path, "a")
			build_file.write(build_file_patch)
			build_file.close()

			process = subprocess.Popen([GRADLEW_CMD, 'copyDependencies'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
			output, err = process.communicate()
			if process.returncode != 0:
				logging.error("Failed to copy gradle dependencies {}".format(project_dir))
				logging.error("Build command: {}".format(process.args))
				logging.error("Build error output: {}".format(err.decode("utf-8")))
				shutil.rmtree(project_dir, onerror=handle_remove_readonly)
				return None

	return project_dir


def download_projects(gitbug_data_dir, output_dir, apply_patch):
	downloaded_projects = []
	for file in os.listdir(gitbug_data_dir):
		project_json = os.path.join(gitbug_data_dir, file)
		for json_Str in open(project_json).read().removesuffix('\n').split('\n'):
			json_Str = json_Str.translate(str.maketrans({"\n": r"\\n"}))
			project_data = json.loads(json_Str)

			project_dir = clone(output_dir, project_data)
			if project_dir == None:
				continue

			project_dir = build(project_data, project_dir, apply_patch)
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
		benchmark['build_id'] = project_dir.split(os.path.sep)[-1]

		src_path = project_dir
		bin_path = ''
		class_path = []

		for word in ['src', 'main', 'java']:
			if os.path.exists(os.path.join(src_path, word)):
				src_path = os.path.join(src_path, word)

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

	if len(sys.argv) < 4:
		logging.error("Usage: `./gutbug_setup.py *path to GitBug root* *path to output dir* *apply patches*`")
		sys.exit(1)

	gitbug_data_dir = os.path.join(sys.argv[1], 'data/bugs/')
	output_dir = sys.argv[2]
	apply_patch = (sys.argv[3] == 'True')

	logging.info("Detected operating system: {}, therefore will use {} and {} to execute gradle and maven tasks when building projects."
				 .format(sys.platform, GRADLEW_CMD, MVN_CMD))
	downloaded_projects = download_projects(gitbug_data_dir, output_dir, apply_patch)
	benchmarks = produce_benchmarks(downloaded_projects)

	output_file_path = os.path.join(output_dir, 'benchmarks.json')
	output_file = open(output_file_path, "w")
	output_file.write(json.dumps(benchmarks, indent = 2))
	output_file.close()


if __name__ == '__main__':
	main()
