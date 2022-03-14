/*
 * Copyright 2020 SIA Joom
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

package com.joom.lightsaber.processor

import java.io.File
import java.nio.file.Path

data class LightsaberParameters(
  val inputs: List<Path>,
  val outputs: List<Path>,
  val classpath: List<Path>,
  val bootClasspath: List<Path>,
  val gen: Path,
  val projectName: String,
  val errorReporter: ErrorReporter = ErrorReporterImpl()
) {

  constructor(
    inputs: List<File>,
    outputs: List<File>,
    classpath: List<File>,
    bootClasspath: List<File>,
    gen: File,
    projectName: String,
    errorReporter: ErrorReporter = ErrorReporterImpl()
  ) : this(
    inputs.map { it.toPath() },
    outputs.map { it.toPath() },
    classpath.map { it.toPath() },
    bootClasspath.map { it.toPath() },
    gen.toPath(),
    projectName,
    errorReporter
  )
}
