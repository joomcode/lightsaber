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

package com.joom.lightsaber.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

abstract class JavaLightsaberPlugin : BaseLightsaberPlugin() {
  override fun apply(project: Project) {
    super.apply(project)

    val lightsaber = project.extensions.create("lightsaber", JavaLightsaberPluginExtension::class.java)

    addDependencies()

    project.afterEvaluate {
      val buildCacheService = registerBuildCacheService<LightsaberTask>()
      if (project.plugins.hasPlugin("java")) {
        setupLightsaberForJava(buildCacheService, lightsaber)
        if (lightsaber.processTest ?: Flags.processTestByDefault(project)) {
          setupLightsaberForJavaTest(buildCacheService, lightsaber)
        }
      } else {
        throw GradleException("Project should use Java plugin")
      }
    }
  }

  private fun addDependencies() {
    addDependencies(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
    addDependencies(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME)
  }

  private fun setupLightsaberForJava(buildCacheService: Provider<LightsaberSharedBuildCacheService>, extension: JavaLightsaberPluginExtension) {
    logger.info("Setting up Lightsaber task for Java project {}...", project.name)
    createTasks(
      sourceSet = project.sourceSets.main,
      compileTask = project.tasks.compileJava,
      classesTask = project.tasks.classes,
      buildCacheService = buildCacheService,
      extension = extension,
    )
  }

  private fun setupLightsaberForJavaTest(buildCacheService: Provider<LightsaberSharedBuildCacheService>, extension: JavaLightsaberPluginExtension) {
    logger.info("Setting up Lightsaber task for Java test project {}...", project.name)
    createTasks(
      sourceSet = project.sourceSets.test,
      compileTask = project.tasks.compileTestJava,
      classesTask = project.tasks.testClasses,
      buildCacheService = buildCacheService,
      extension = extension,
      nameSuffix = "test"
    )
  }

  private fun createTasks(
    sourceSet: SourceSet,
    compileTask: JavaCompile,
    classesTask: Task,
    buildCacheService: Provider<LightsaberSharedBuildCacheService>,
    extension: JavaLightsaberPluginExtension,
    nameSuffix: String = ""
  ) {
    val suffix = nameSuffix.capitalize()
    val lightsaberDir = File(project.buildDir, getLightsaberRelativePath(nameSuffix))
    val classesDirs = getClassesDirs(sourceSet.output)
    val backupDirs = getBackupDirs(project.buildDir, lightsaberDir, classesDirs)
    val sourceDir = File(lightsaberDir, "src")
    val classpath = compileTask.classpath.toList() - classesDirs.toSet()
    val modulesClasspath = modulesClasspathProvider(project.configurations.named(sourceSet.runtimeClasspathConfigurationName))

    val bootClasspath = compileTask.options.bootstrapClasspath?.toList()
      ?: System.getProperty("sun.boot.class.path")?.split(File.pathSeparator)?.map { File(it) }
      ?: emptyList()

    val lightsaberTask = createLightsaberProcessTask(
      taskName = "${LightsaberTask.TASK_PREFIX}$suffix",
      classesDirs = classesDirs,
      backupDirs = backupDirs,
      sourceDir = sourceDir,
      classpath = classpath,
      modulesClasspath = modulesClasspath,
      bootClasspath = bootClasspath,
      buildEntityService = buildCacheService,
      extension = extension
    )

    val backupTask = createBackupClassFilesTask(
      taskName = "lightsaberBackupClasses$suffix",
      classesDirs = classesDirs,
      backupDirs = backupDirs
    )

    configureTasks(lightsaberTask, backupTask, compileTask, classesTask)
  }

  private fun modulesClasspathProvider(configuration: Provider<Configuration>): Provider<FileCollection> {
    return configuration.map { it.incomingJarArtifacts { it is ProjectComponentIdentifier }.artifactFiles }
  }

  private fun getLightsaberRelativePath(suffix: String): String {
    return if (suffix.isEmpty()) LIGHTSABER_PATH else LIGHTSABER_PATH + File.separatorChar + suffix
  }

  private fun getClassesDirs(output: SourceSetOutput): List<File> {
    return output.classesDirs.files.toList()
  }

  private fun getBackupDirs(buildDir: File, lightsaberDir: File, classesDirs: List<File>): List<File> {
    return classesDirs.map { classesDir ->
      val relativeFile = classesDir.relativeToOrSelf(buildDir)
      // XXX: What if relativeFile is rooted? Maybe we need to remove the root part from it.
      File(lightsaberDir, relativeFile.path)
    }
  }

  private fun configureTasks(lightsaberTask: LightsaberTask, backupTask: BackupClassesTask, compileTask: Task, classesTask: Task) {
    backupTask.dependsOn(compileTask)
    lightsaberTask.mustRunAfter(compileTask)
    lightsaberTask.dependsOn(compileTask)
    lightsaberTask.dependsOn(backupTask)
    classesTask.dependsOn(lightsaberTask)

    val cleanBackupTask = project.tasks["clean${backupTask.name.capitalize()}"]!!
    val cleanLightsaberTask = project.tasks["clean${lightsaberTask.name.capitalize()}"]!!

    cleanBackupTask.doFirst {
      backupTask.clean()
    }

    cleanLightsaberTask.doFirst {
      lightsaberTask.clean()
    }

    cleanLightsaberTask.dependsOn(cleanBackupTask)
  }

  private fun createLightsaberProcessTask(
    taskName: String,
    classesDirs: Collection<File>,
    backupDirs: Collection<File>,
    sourceDir: File,
    classpath: Collection<File>,
    modulesClasspath: Provider<FileCollection>,
    bootClasspath: Collection<File>,
    buildEntityService: Provider<LightsaberSharedBuildCacheService>,
    extension: JavaLightsaberPluginExtension,
  ): LightsaberTask {
    logger.info("Creating Lightsaber task {}...", taskName)
    logger.info("  Source classes directories: {}", backupDirs)
    logger.info("  Processed classes directories: {}", classesDirs)

    val validateUsage = extension.validateUsage ?: Flags.validateUsageByDefault(project)
    val validateUnusedImports = extension.validateUnusedImports ?: Flags.validateUnusedImportsByDefault(project)
    val validateUnusedImportsVerbose = extension.validateUnusedImportsVerbose ?: Flags.validateUnusedImportsVerboseByDefault(project)
    val dumpDebugReport = extension.dumpDebugReport ?: Flags.dumpDebugReportByDefault(project)

    return project.tasks.create(taskName, LightsaberTask::class.java) { task ->
      task.description = "Processes .class files with Lightsaber Processor."
      task.inputDirectories.from(backupDirs)
      task.outputDirectories.from(classesDirs)
      task.sourceDir.set(sourceDir)
      task.classpath.from(classpath)
      task.modulesClasspath.from(modulesClasspath)
      task.bootClasspath.from(bootClasspath)
      task.sharedBuildCacheService.set(buildEntityService)
      task.validateUsage.set(validateUsage)
      task.validateUnusedImports.set(validateUnusedImports)
      task.validateUnusedImportsVerbose.set(validateUnusedImportsVerbose)
      task.dumpDebugReport.set(dumpDebugReport)
      @Suppress("UnstableApiUsage")
      task.usesService(buildEntityService)
    }
  }

  private fun createBackupClassFilesTask(
    taskName: String,
    classesDirs: List<File>,
    backupDirs: List<File>
  ): BackupClassesTask {
    return project.tasks.create(taskName, BackupClassesTask::class.java) { task ->
      task.description = "Back up original .class files."
      task.classesDirs = classesDirs
      task.backupDirs = backupDirs
    }
  }

  private fun String.capitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
  }

  companion object {
    private const val LIGHTSABER_PATH = "lightsaber"
  }
}
