/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liferay.blade.cli;

import java.io.File;
import java.io.InputStream;

import java.net.URL;

import java.nio.file.Files;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

/**
 * @author David Truong
 */
public class SamplesCommand {

	public SamplesCommand(BladeCLI blade, SamplesCommandArgs options) throws Exception {
		_blade = blade;
		_options = options;
	}

	public void execute() throws Exception {
		final String sampleName = _options.getSampleName();

		if (_downloadBladeRepoIfNeeded()) {
			_extractBladeRepo();
		}

		if (sampleName == null) {
			_listSamples();
		}
		else {
			_copySample(sampleName);
		}
	}

	private void _addGradleWrapper(File dest) throws Exception {
		InputStream in = SamplesCommand.class.getResourceAsStream("/wrapper.zip");

		Util.copy(in, dest);

		new File(dest, "gradlew").setExecutable(true);
	}

	private void _copySample(String sampleName) throws Exception {
		File workDir = _options.getDir();

		if (workDir == null) {
			workDir = _blade.getBase();
		}

		File bladeRepo = new File(_blade.getCacheDir(), _BLADE_REPO_NAME);

		File gradleSamples = new File(bladeRepo, "gradle");

		for (File file : gradleSamples.listFiles()) {
			String fileName = file.getName();

			if (file.isDirectory() && fileName.equals(sampleName)) {
				File dest = new File(workDir, fileName);

				FileUtils.copyDirectory(file, dest);

				_updateBuildGradle(dest);

				if (!Util.hasGradleWrapper(dest)) {
					_addGradleWrapper(dest);
				}
			}
		}
	}

	private String _deindent(String s) {
		return s.replaceAll("(?m)^\t", "");
	}

	private boolean _downloadBladeRepoIfNeeded() throws Exception {
		File bladeRepoArchive = new File(_blade.getCacheDir(), _BLADE_REPO_ARCHIVE_NAME);

		Date now = new Date();

		long diff = now.getTime() - bladeRepoArchive.lastModified();

		if (!bladeRepoArchive.exists() || (diff > _FILE_EXPIRATION_TIME)) {
			FileUtils.copyURLToFile(new URL(_BLADE_REPO_URL), bladeRepoArchive);

			return true;
		}

		return false;
	}

	private void _extractBladeRepo() throws Exception {
		File bladeRepoArchive = new File(_blade.getCacheDir(), _BLADE_REPO_ARCHIVE_NAME);

		Util.unzip(bladeRepoArchive, _blade.getCacheDir(), null);
	}

	private void _listSamples() {
		File bladeRepo = new File(_blade.getCacheDir(), _BLADE_REPO_NAME);

		File gradleSamples = new File(bladeRepo, "gradle");

		List<String> samples = new ArrayList<>();

		for (File file : gradleSamples.listFiles()) {
			String fileName = file.getName();

			if (file.isDirectory() && fileName.startsWith("blade.")) {
				samples.add(fileName);
			}
		}

		_blade.out().println("Please provide the sample project name to create, e.g. \"blade samples blade.rest\"\n");
		_blade.out().println("Currently available samples:");
		_blade.out().println(WordUtils.wrap(StringUtils.join(samples, ", "), 80));
	}

	private String _parseGradleScript(String script, String section, boolean contentsOnly) {
		int begin = script.indexOf(section + " {");

		int end = begin;

		int count = 0;

		if (contentsOnly) {
			begin += section.length() + 2;
		}

		while (true) {
			char c = script.charAt(end);

			if ((count != 0) && (c == '}')) {
				count--;
			}
			else if (c == '{') {
				count++;
			}

			if ((count == 0) && (c == '}')) {
				if (!contentsOnly) {
					end++;
				}

				break;
			}

			end++;
		}

		String newScript = script.substring(begin, end);

		if (contentsOnly) {
			return _deindent(newScript);
		}

		return newScript;
	}

	private String _removeGradleSection(String script, String section) {
		int begin = script.indexOf(section + " {");

		int end = begin;

		int count = 0;

		if (begin == -1) {
			return script;
		}

		while (true) {
			char c = script.charAt(end);

			if ((count != 0) && (c == '}')) {
				count--;
			}
			else if (c == '{') {
				count++;
			}

			end++;

			if ((count == 0) && (c == '}')) {
				break;
			}
		}

		return _removeGradleSection(script.substring(0, begin) + script.substring(end, script.length()), section);
	}

	private void _updateBuildGradle(File dir) throws Exception {
		File bladeRepo = new File(_blade.getCacheDir(), _BLADE_REPO_NAME);

		File sampleGradleFile = new File(dir, "build.gradle");

		String script = Util.read(sampleGradleFile);

		if (!Util.isWorkspace(dir)) {
			File parentBuildGradleFile = new File(bladeRepo, "gradle/build.gradle");

			String parentBuildScript = _parseGradleScript(Util.read(parentBuildGradleFile), "buildscript", false);

			String parentSubprojectsScript = _parseGradleScript(Util.read(parentBuildGradleFile), "subprojects", true);

			parentSubprojectsScript = _removeGradleSection(parentSubprojectsScript, "buildscript");

			System.out.println(parentSubprojectsScript);

			script = parentBuildScript + parentSubprojectsScript + script;
		}

		Files.write(sampleGradleFile.toPath(), script.getBytes());
	}

	private static final String _BLADE_REPO_ARCHIVE_NAME = "liferay-blade-samples-2.x.zip";

	private static final String _BLADE_REPO_NAME = "liferay-blade-samples-2.x";

	private static final String _BLADE_REPO_URL = "https://github.com/liferay/liferay-blade-samples/archive/2.x.zip";

	private static final long _FILE_EXPIRATION_TIME = 604800000;

	private final BladeCLI _blade;
	private final SamplesCommandArgs _options;

}