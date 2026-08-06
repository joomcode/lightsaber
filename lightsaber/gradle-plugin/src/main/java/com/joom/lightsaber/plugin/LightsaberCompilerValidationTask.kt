/*
 * Copyright 2026 SIA Joom
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

import com.joom.lightsaber.processor.LightsaberSharedBuildCache
import com.joom.lightsaber.processor.LightsaberValidator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import java.io.File
import java.net.URI
import java.nio.file.FileSystems

@CacheableTask
abstract class LightsaberCompilerValidationTask : DefaultTask() {
  @get:Classpath
  abstract val inputClasses: ConfigurableFileCollection

  @get:Classpath
  abstract val inputJars: ListProperty<RegularFile>

  @get:Classpath
  abstract val inputDirectories: ListProperty<Directory>

  @get:CompileClasspath
  abstract val classpath: ConfigurableFileCollection

  @get:CompileClasspath
  abstract val modulesClasspath: ConfigurableFileCollection

  @get:CompileClasspath
  abstract val bootClasspath: ConfigurableFileCollection

  @get:Input
  abstract val validateUsage: Property<Boolean>

  @get:Input
  abstract val validateUnusedImports: Property<Boolean>

  @get:Input
  abstract val validateUnusedImportsVerbose: Property<Boolean>

  @get:Input
  abstract val dumpDebugReport: Property<Boolean>

  @get:Optional
  @get:OutputFile
  abstract val debugReport: RegularFileProperty

  @get:OutputFile
  abstract val result: RegularFileProperty

  @get:Internal
  abstract val compilationSuccessMarker: RegularFileProperty

  @TaskAction
  fun validate() {
    val inputs = (
      inputClasses.files +
        inputJars.get().map { it.asFile } +
        inputDirectories.get().map { it.asFile }
      )
      .filter(File::exists)
      .distinct()
      .map(File::toPath)
    val debugReport = debugReport.get().asFile.takeIf { dumpDebugReport.get() }
    if (debugReport == null) {
      this.debugReport.get().asFile.delete()
    }
    if (inputs.isEmpty() || (!validateUsage.get() && !validateUnusedImports.get() && debugReport == null)) {
      writeResult()
      return
    }

    val bootClasspath = bootClasspath.files.filter(File::exists).map(File::toPath).ifEmpty {
      listOfNotNull(
        runCatching {
          FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules", "java.base")
        }.getOrNull()
      )
    }
    val errors = LightsaberSharedBuildCache.create().use { cache ->
      LightsaberValidator.validate(
        inputs = inputs,
        classpath = classpath.files.filter(File::exists).map(File::toPath),
        modulesClasspath = modulesClasspath.files.filter(File::exists).map(File::toPath),
        bootClasspath = bootClasspath,
        projectName = path.replace(':', '$'),
        validateUsage = validateUsage.get(),
        validateUnusedImports = validateUnusedImports.get(),
        validateUnusedImportsVerbose = validateUnusedImportsVerbose.get(),
        debugReport = debugReport,
        sharedBuildCache = cache,
      )
    }
    if (errors.isNotEmpty()) {
      throw GradleException(errors.joinToString(prefix = "Lightsaber validation failed:\n", separator = "\n"))
    }
    writeResult()
  }

  private fun writeResult() {
    result.get().asFile.apply {
      parentFile.mkdirs()
      writeText("success\n")
    }
  }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal fun Project.registerLightsaberCompilerValidation(
  compilation: KotlinCompilation<*>,
  includeAndroidJavaSourceDirectories: Boolean,
  bootClasspath: FileCollection,
  validateUsage: Provider<Boolean>,
  validateUnusedImports: Provider<Boolean>,
  validateUnusedImportsVerbose: Provider<Boolean>,
  dumpDebugReport: Provider<Boolean>,
): TaskProvider<LightsaberCompilerValidationTask> {
  val compileConfiguration = configurations.getByName(compilation.compileDependencyConfigurationName)
  val runtimeConfiguration = configurations.getByName(
    compilation.runtimeDependencyConfigurationName ?: compilation.compileDependencyConfigurationName
  )
  val compileClasspath = if (includeAndroidJavaSourceDirectories) {
    compileConfiguration.incomingAndroidJarArtifacts().artifactFiles
  } else {
    compileConfiguration.incomingJarArtifacts().artifactFiles
  }
  val runtimeClasspath = if (includeAndroidJavaSourceDirectories) {
    runtimeConfiguration.incomingAndroidJarArtifacts().artifactFiles
  } else {
    runtimeConfiguration.incomingJarArtifacts().artifactFiles
  }
  val modulesClasspath = if (includeAndroidJavaSourceDirectories) {
    runtimeConfiguration.incomingAndroidJarArtifacts { it is ProjectComponentIdentifier }.artifactFiles
  } else {
    runtimeConfiguration.incomingJarArtifacts { it is ProjectComponentIdentifier }.artifactFiles
  }
  val taskName = "lightsaberValidate" + compilation.compileTaskProvider.name.replaceFirstChar { it.uppercaseChar() }
  val compilationSuccessDirectory = layout.buildDirectory.dir(
    "intermediates/lightsaber-compilation-success/${compilation.name}"
  )
  val compilationSuccessMarker = compilationSuccessDirectory.map { it.file("success.txt") }
  val validationTask = tasks.register(taskName, LightsaberCompilerValidationTask::class.java) { task ->
    if (!includeAndroidJavaSourceDirectories) {
      task.inputClasses.from(compilation.output.classesDirs)
    }
    task.inputJars.convention(emptyList())
    task.inputDirectories.convention(emptyList())
    task.classpath.from(validateUsage.map { enabled ->
      if (enabled) runtimeClasspath else compileClasspath
    })
    task.modulesClasspath.from(validateUsage.map { enabled ->
      if (enabled) modulesClasspath else files()
    })
    task.bootClasspath.from(bootClasspath)
    task.validateUsage.set(validateUsage)
    task.validateUnusedImports.set(validateUnusedImports)
    task.validateUnusedImportsVerbose.set(validateUnusedImportsVerbose)
    task.dumpDebugReport.set(dumpDebugReport)
    task.debugReport.set(layout.buildDirectory.file("reports/lightsaber/${compilation.name}/debug-dump.txt"))
    task.result.set(layout.buildDirectory.file("intermediates/lightsaber-validation/${compilation.name}/result.txt"))
    task.compilationSuccessMarker.set(compilationSuccessMarker)
    task.onlyIf("Kotlin compilation completed without a failure") {
      task.compilationSuccessMarker.get().asFile.isFile
    }
  }
  val compileTask = compilation.compileTaskProvider
  compileTask.configure { task ->
    task.outputs.dir(compilationSuccessDirectory)
      .withPropertyName("lightsaberCompilationSuccessDirectory")
    task.doFirst {
      compilationSuccessMarker.get().asFile.delete()
    }
    task.doLast {
      compilationSuccessMarker.get().asFile.apply {
        parentFile.mkdirs()
        writeText("success\n")
      }
    }
    task.finalizedBy(validationTask)
  }
  return validationTask
}
