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

import com.joom.lightsaber.processor.LightsaberOutputFactory
import com.joom.lightsaber.processor.LightsaberParameters
import com.joom.lightsaber.processor.LightsaberProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Paths
import javax.inject.Inject

@CacheableTask
abstract class LightsaberTransformTask @Inject constructor(
  private val projectLayout: ProjectLayout
) : DefaultTask() {
  @get:InputFiles
  @get:Classpath
  abstract val inputClasses: ListProperty<RegularFile>

  @get:InputFiles
  @get:Classpath
  abstract val inputDirectories: ListProperty<Directory>

  @get:InputFiles
  @get:CompileClasspath
  abstract val bootClasspath: ConfigurableFileCollection

  @get:InputFiles
  @get:CompileClasspath
  abstract val classpath: ConfigurableFileCollection

  @get:InputFiles
  @get:CompileClasspath
  abstract val modulesClasspath: ConfigurableFileCollection

  @get:OutputFile
  @get:Optional
  abstract val output: RegularFileProperty

  @get:OutputDirectory
  @get:Optional
  abstract val outputDirectory: DirectoryProperty

  @get:Internal
  @Suppress("UnstableApiUsage")
  abstract val sharedBuildCacheService: Property<LightsaberSharedBuildCacheService>

  @get:Input
  abstract val validateUsage: Property<Boolean>

  @get:Input
  abstract val validateUnusedImports: Property<Boolean>

  @get:Input
  abstract val validateUnusedImportsVerbose: Property<Boolean>

  @get:Input
  abstract val dumpDebugReport: Property<Boolean>

  private val projectName = formatProjectName()

  init {
    logging.captureStandardOutput(LogLevel.LIFECYCLE)
  }

  @TaskAction
  fun process() {
    clean()

    val output = computeOutput().get().toPath()
    val reports = computeReportDirectory().toPath()

    val parameters = LightsaberParameters(
      inputs = inputClasses.get().map { it.asFile.toPath() } + inputDirectories.get().map { it.asFile.toPath() },
      outputFactory = LightsaberOutputFactory.create(output),
      classpath = classpath.map { it.toPath() },
      bootClasspath = bootClasspath.map { it.toPath() },
      modulesClasspath = modulesClasspath.map { it.toPath() },
      projectName = projectName,
      validateUsage = validateUsage.get(),
      validateUnusedImports = validateUnusedImports.get(),
      validateUnusedImportsVerbose = validateUnusedImportsVerbose.get(),
      dumpDebugReport = dumpDebugReport.get(),
      reportDirectory = reports,
      sharedBuildCache = sharedBuildCacheService.get().cache,
    )

    logger.info("Starting Lightsaber processor: {}", parameters)

    val processor = LightsaberProcessor(parameters)
    try {
      processor.process()
    } catch (exception: Exception) {
      throw GradleScriptException("Lightsaber processor failed to process files", exception)
    }
  }

  private fun clean() {
    val output = computeOutput()
    val reports = computeReportDirectory()

    if (output.get().exists()) {
      output.get().deleteRecursively()
    }

    if (reports.exists()) {
      reports.deleteRecursively()
    }
  }

  private fun computeOutput(): Provider<File> {
    return when {
      output.isPresent -> output.asFile
      outputDirectory.isPresent -> outputDirectory.asFile
      else -> error("output or outputDirectory is not set")
    }
  }

  private fun computeReportDirectory(): File {
    return Paths.get(projectLayout.buildDirectory.get().asFile.path, "reports", "lightsaber").toFile()
  }

  companion object {
    const val TASK_PREFIX = "lightsaberTransformClasses"
  }
}
