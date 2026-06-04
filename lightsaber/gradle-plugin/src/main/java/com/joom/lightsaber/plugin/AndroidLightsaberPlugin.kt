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

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Component
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider

abstract class AndroidLightsaberPlugin : BaseLightsaberPlugin() {
  override fun apply(project: Project) {
    super.apply(project)

    if (!project.hasAndroid) {
      throw GradleException("Lightsaber plugin must be applied *AFTER* Android plugin")
    }

    val androidComponents = project.androidComponents
      ?: throw GradleException(
        "Lightsaber Android plugin requires Android Gradle Plugin $MIN_AGP_VERSION or newer " +
          "(androidComponents extension is missing)"
      )

    if (androidComponents.pluginVersion < MIN_AGP_VERSION) {
      throw GradleException(
        "Lightsaber Android plugin requires Android Gradle Plugin $MIN_AGP_VERSION or newer, " +
          "but ${androidComponents.pluginVersion} is used"
      )
    }

    addDependencies(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)

    val extension = project.extensions.create("lightsaber", AndroidLightsaberPluginExtension::class.java)
    val buildCacheService = registerBuildCacheService<LightsaberTransformTask>()

    val validateUsageByDefault = Flags.validateUsageByDefault(project)
    val validateUnusedImportsByDefault = Flags.validateUnusedImportsByDefault(project)
    val validateUnusedImportsVerboseByDefault = Flags.validateUnusedImportsVerboseByDefault(project)
    val dumpDebugReportByDefault = Flags.dumpDebugReportByDefault(project)

    val validateUsage = project.provider { extension.validateUsage ?: validateUsageByDefault }
    val validateUnusedImports = project.provider { extension.validateUnusedImports ?: validateUnusedImportsByDefault }
    val validateUnusedImportsVerbose = project.provider { extension.validateUnusedImportsVerbose ?: validateUnusedImportsVerboseByDefault }
    val dumpDebugReport = project.provider { extension.dumpDebugReport ?: dumpDebugReportByDefault }

    configureVariants(
      components = project.applicationAndroidComponents,
      extension = extension,
      validateUsage = validateUsage,
      validateUnusedImports = validateUnusedImports,
      validateUnusedImportsVerbose = validateUnusedImportsVerbose,
      dumpDebugReport = dumpDebugReport,
      buildCacheService = buildCacheService,
    )

    configureVariants(
      components = project.libraryAndroidComponents,
      extension = extension,
      validateUsage = validateUsage,
      validateUnusedImports = validateUnusedImports,
      validateUnusedImportsVerbose = validateUnusedImportsVerbose,
      dumpDebugReport = dumpDebugReport,
      buildCacheService = buildCacheService,
    )
  }

  private fun configureVariants(
    components: AndroidComponentsExtension<*, *, *>?,
    extension: AndroidLightsaberPluginExtension,
    validateUsage: Provider<Boolean>,
    validateUnusedImports: Provider<Boolean>,
    validateUnusedImportsVerbose: Provider<Boolean>,
    dumpDebugReport: Provider<Boolean>,
    buildCacheService: Provider<LightsaberSharedBuildCacheService>,
  ) {
    components?.onVariants(components.selector().all()) { variant ->
      variant.registerLightsaberTasks(
        extension = extension,
        validateUsage = validateUsage,
        validateUnusedImports = validateUnusedImports,
        validateUnusedImportsVerbose = validateUnusedImportsVerbose,
        dumpDebugReport = dumpDebugReport,
        buildCacheService = buildCacheService,
      )
    }
  }

  private fun Variant.registerLightsaberTasks(
    extension: AndroidLightsaberPluginExtension,
    validateUsage: Provider<Boolean>,
    validateUnusedImports: Provider<Boolean>,
    validateUnusedImportsVerbose: Provider<Boolean>,
    dumpDebugReport: Provider<Boolean>,
    buildCacheService: Provider<LightsaberSharedBuildCacheService>,
  ) {
    val runtimeClasspath = runtimeClasspathConfiguration()

    registerLightsaberTask(
      validateUsage = validateUsage,
      validateUnusedImports = validateUnusedImports,
      validateUnusedImportsVerbose = validateUnusedImportsVerbose,
      dumpDebugReport = dumpDebugReport,
      classpathProvider = classpathProvider(runtimeClasspath),
      modulesClasspathProvider = modulesClasspathProvider(runtimeClasspath),
      buildCacheService = buildCacheService,
      cacheable = extension.cacheable,
    )

    if (this is HasAndroidTest) {
      val androidTestComponent = androidTest ?: return
      androidTestComponent.registerLightsaberTask(
        validateUsage = validateUsage,
        validateUnusedImports = validateUnusedImports,
        validateUnusedImportsVerbose = validateUnusedImportsVerbose,
        dumpDebugReport = dumpDebugReport,
        classpathProvider = classpathProvider(androidTestComponent.runtimeClasspathConfiguration()),
        modulesClasspathProvider = modulesClasspathProvider(androidTestComponent.runtimeClasspathConfiguration()) - modulesClasspathProvider(runtimeClasspath),
        buildCacheService = buildCacheService,
        cacheable = extension.cacheable,
      )
    }
  }

  private fun Component.registerLightsaberTask(
    validateUsage: Provider<Boolean>,
    validateUnusedImports: Provider<Boolean>,
    validateUnusedImportsVerbose: Provider<Boolean>,
    dumpDebugReport: Provider<Boolean>,
    classpathProvider: Provider<FileCollection>,
    modulesClasspathProvider: Provider<FileCollection>,
    buildCacheService: Provider<LightsaberSharedBuildCacheService>,
    cacheable: Boolean,
  ) {
    val taskProvider = project.registerTask<LightsaberTransformTask>(
      LightsaberTransformTask.TASK_PREFIX + name.replaceFirstChar { it.uppercaseChar() }
    )

    artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
      .use(taskProvider)
      .toTransform(
        ScopedArtifact.CLASSES,
        LightsaberTransformTask::allJars,
        LightsaberTransformTask::allDirectories,
        LightsaberTransformTask::output,
      )

    taskProvider.configure { task ->
      task.classpath.setFrom(classpathProvider)
      task.modulesClasspath.setFrom(modulesClasspathProvider)

      @Suppress("UnstableApiUsage")
      task.bootClasspath.from(project.androidComponents!!.sdkComponents.bootClasspath)
      task.sharedBuildCacheService.set(buildCacheService)
      task.validateUsage.set(validateUsage)
      task.validateUnusedImports.set(validateUnusedImports)
      task.validateUnusedImportsVerbose.set(validateUnusedImportsVerbose)
      task.dumpDebugReport.set(dumpDebugReport)

      if (!cacheable) {
        task.outputs.doNotCacheIf("lightsaber.cacheable is false") { true }
      }

      @Suppress("UnstableApiUsage")
      task.usesService(buildCacheService)
    }
  }

  private fun Component.runtimeClasspathConfiguration(): Provider<Configuration> {
    return project.configurations.named(name + "RuntimeClasspath")
  }

  private fun classpathProvider(configuration: Provider<Configuration>): Provider<FileCollection> {
    return configuration.map { it.incomingAndroidJarArtifacts().artifactFiles }
  }

  private fun modulesClasspathProvider(configuration: Provider<Configuration>): Provider<FileCollection> {
    return configuration.map { it.incomingAndroidJarArtifacts { it is ProjectComponentIdentifier }.artifactFiles }
  }

  private operator fun Provider<FileCollection>.minus(other: Provider<FileCollection>): Provider<FileCollection> {
    return zip(other) { first, second -> first - second }
  }

  private companion object {
    private val MIN_AGP_VERSION = AndroidPluginVersion(major = 7, minor = 4, micro = 0)
  }
}
