/*
 * Copyright 2022 SIA Joom
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

import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.spi.FileSystemProvider

data class LightsaberParameters(
  val inputs: List<Path>,
  val outputs: List<Path>,
  val classpath: List<Path>,
  val bootClasspath: List<Path>,
  val gen: Path,
  val projectName: String,
  val errorReporter: ErrorReporter = ErrorReporterImpl()
) {
  companion object {
    val RT_PATH by lazy {
      val installedFilesystemSets = FileSystemProvider.installedProviders().map { it.scheme }.toSet()
      if (installedFilesystemSets.contains("jrt")) {
        listOf(Paths.get(URI.create("jrt:/")).resolve("/modules/java.base"))
      } else {
        emptyList<Path>()
      }
    }
  }
}
