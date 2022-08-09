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

package com.joom.lightsaber.plugin

import com.joom.lightsaber.processor.LightsaberParameters
import com.joom.lightsaber.processor.LightsaberProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class LightsaberTransformTask : DefaultTask() {

  @get:InputFiles
  @get:Classpath
  abstract val inputClasses: ListProperty<Directory>

  @get:InputFiles
  @get:CompileClasspath
  abstract val bootClasspath: ConfigurableFileCollection

  @get:InputFiles
  @get:CompileClasspath
  abstract val classpath: ConfigurableFileCollection

  @get:InputFiles
  @get:CompileClasspath
  abstract val modulesClasspath: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  private val projectName = formatProjectName()

  init {
    logging.captureStandardOutput(LogLevel.LIFECYCLE)
  }

  @TaskAction
  fun process() {
    val output = outputDirectory.get().asFile.toPath()
    val parameters = LightsaberParameters(
      inputs = inputClasses.get().map { it.asFile.toPath() },
      outputs = List(inputClasses.get().size) { output },
      classpath = classpath.map { it.toPath() },
      bootClasspath = bootClasspath.map { it.toPath() },
      modulesClasspath = modulesClasspath.map { it.toPath() },
      gen = output,
      projectName = projectName,
    )

    logger.info("Starting Lightsaber processor: {}", parameters)

    val processor = LightsaberProcessor(parameters)
    try {
      processor.process()
    } catch (exception: Exception) {
      throw GradleScriptException("Lightsaber processor failed to process files", exception)
    }
  }

  companion object {
    const val TASK_PREFIX = "lightsaberTransformClasses"
  }
}
