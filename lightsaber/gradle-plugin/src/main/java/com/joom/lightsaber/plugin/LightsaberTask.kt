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
import com.joom.lightsaber.processor.watermark.WatermarkChecker
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Paths
import javax.inject.Inject

abstract class LightsaberTask @Inject constructor(
  private val projectLayout: ProjectLayout
) : DefaultTask() {
  @get:InputFiles
  @get:Classpath
  abstract val backupDirs: ConfigurableFileCollection

  @get:OutputDirectories
  abstract val classesDirs: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val sourceDir: DirectoryProperty

  @get:InputFiles
  @get:CompileClasspath
  abstract val classpath: ConfigurableFileCollection

  @get:InputFiles
  @get:CompileClasspath
  abstract val modulesClasspath: ConfigurableFileCollection

  @get:InputFiles
  @get:CompileClasspath
  abstract val bootClasspath: ConfigurableFileCollection

  @get:Internal
  @Suppress("UnstableApiUsage")
  abstract val sharedBuildCacheService: Property<LightsaberSharedBuildCacheService>

  @get:Input
  abstract val validateUsage: Property<Boolean>

  @get:Input
  abstract val dumpDebugReport: Property<Boolean>

  private val projectName = formatProjectName()

  init {
    logging.captureStandardOutput(LogLevel.INFO)
  }

  @TaskAction
  fun process() {
    validate()

    val parameters = LightsaberParameters(
      inputs = backupDirs.map { it.toPath() },
      outputs = classesDirs.map { it.toPath() },
      classpath = classpath.map { it.toPath() },
      modulesClasspath = modulesClasspath.map { it.toPath() },
      bootClasspath = bootClasspath.map { it.toPath() }.ifEmpty {
        listOfNotNull(FileSystems.getFileSystem(URI.create("jrt:/"))?.getPath("modules", "java.base"))
      },
      gen = classesDirs.first().toPath(),
      projectName = projectName,
      sharedBuildCache = sharedBuildCacheService.get().cache,
      validateUsage = validateUsage.get(),
      dumpDebugReport = dumpDebugReport.get(),
      reportDirectory = computeReportDirectory().toPath()
    )

    logger.info("Starting Lightsaber processor: {}", parameters)
    val processor = LightsaberProcessor(parameters)
    try {
      processor.process()
    } catch (exception: Exception) {
      throw GradleScriptException("Lightsaber processor failed to process files", exception)
    }
  }

  fun clean() {
    validate()
    logger.info("Removing patched files from {}", classesDirs)

    for (classesDir in classesDirs) {
      if (!classesDir.exists()) {
        continue
      }

      classesDir.walkBottomUp().forEach { file ->
        if (file.isDirectory) {
          file.delete()
        } else {
          logger.debug("Checking {}...", file)
          if (WatermarkChecker.isLightsaberClass(file)) {
            logger.debug("File was patched - removing")
            file.delete()
          } else {
            logger.debug("File wasn't patched - skipping")
          }
        }
      }
    }

    if (sourceDir.isPresent) {
      logger.info("Removing a directory with generated source files: {}", sourceDir)
      sourceDir.get().asFile.deleteRecursively()
    }

    computeReportDirectory().deleteRecursively()
  }

  private fun validate() {
    require(!classesDirs.isEmpty) { "classesDirs is not set" }
    require(!backupDirs.isEmpty) { "backupDirs is not set" }
    require(classesDirs.files.size == backupDirs.files.size) { "classesDirs and backupDirs must have equal size" }
    require(sourceDir.isPresent) { "sourceDir is not set" }
  }

  private fun computeReportDirectory(): File {
    return Paths.get(projectLayout.buildDirectory.get().asFile.path, "reports", "lightsaber").toFile()
  }

  companion object {
    const val TASK_PREFIX = "lightsaberProcess"
  }
}
