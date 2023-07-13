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
import com.android.build.api.variant.Component
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.Variant
import com.android.build.gradle.AppExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import java.io.File
import java.nio.file.Paths

abstract class AndroidLightsaberPlugin : BaseLightsaberPlugin() {
  override fun apply(project: Project) {
    super.apply(project)

    if (!project.hasAndroid) {
      throw GradleException("Lightsaber plugin must be applied *AFTER* Android plugin")
    }

    addDependencies(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)

    val extension = project.extensions.create("lightsaber", AndroidLightsaberPluginExtension::class.java)
    val componentsExtension = project.androidComponents

    when {
      componentsExtension != null && componentsExtension.pluginVersion >= SCOPED_ARTIFACTS_VERSION -> {
        logger.info("Registering lightsaber with scoped artifacts API")

        configureTransformWithArtifactsApi(ScopedArtifactsRegistrar, extension, registerBuildCacheService<LightsaberTransformTask>())
      }

      componentsExtension != null && componentsExtension.pluginVersion >= ALL_CLASSES_TRANSFORM_API_VERSION -> {
        logger.info("Registering lightsaber with all classes transform API")

        configureTransformWithArtifactsApi(AllClassesTransformRegistrar, extension, registerBuildCacheService<LightsaberTransformTask>())
      }

      else -> {
        logger.info("Registering lightsaber with transform API")

        configureTransform(extension)
      }
    }
  }

  private fun configureTransformWithArtifactsApi(
    registrar: TransformTaskRegistrar,
    extension: AndroidLightsaberPluginExtension,
    buildCacheService: Provider<LightsaberSharedBuildCacheService>
  ) {
    val validateUsageByDefault = Flags.validateUsageByDefault(project)
    val validateUnusedImportsByDefault = Flags.validateUnusedImportsByDefault(project)
    val validateUnusedImportsVerboseByDefault = Flags.validateUnusedImportsVerboseByDefault(project)
    val dumpDebugReportByDefault = Flags.dumpDebugReportByDefault(project)

    val validateUsage = project.provider { extension.validateUsage ?: validateUsageByDefault }
    val validateUnusedImports = project.provider { extension.validateUnusedImports ?: validateUnusedImportsByDefault }
    val validateUnusedImportsVerbose = project.provider { extension.validateUnusedImportsVerbose ?: validateUnusedImportsVerboseByDefault }
    val dumpDebugReport = project.provider { extension.dumpDebugReport ?: dumpDebugReportByDefault }

    project.applicationAndroidComponents?.apply {
      onVariants(selector().all()) { variant ->
        variant.registerLightsaberTask(
          registrar = registrar,
          validateUsage = validateUsage,
          validateUnusedImports = validateUnusedImports,
          validateUnusedImportsVerbose = validateUnusedImportsVerbose,
          dumpDebugReport = dumpDebugReport,
          buildCacheService = buildCacheService
        )
      }
    }

    project.libraryAndroidComponents?.apply {
      onVariants(selector().all()) { variant ->
        variant.registerLightsaberTask(
          registrar = registrar,
          validateUsage = validateUsage,
          validateUnusedImports = validateUnusedImports,
          validateUnusedImportsVerbose = validateUnusedImportsVerbose,
          dumpDebugReport = dumpDebugReport,
          buildCacheService = buildCacheService
        )
      }
    }
  }

  private fun <T> T.registerLightsaberTask(
    registrar: TransformTaskRegistrar,
    validateUsage: Provider<Boolean>,
    validateUnusedImports: Provider<Boolean>,
    validateUnusedImportsVerbose: Provider<Boolean>,
    dumpDebugReport: Provider<Boolean>,
    buildCacheService: Provider<LightsaberSharedBuildCacheService>,
  ) where T : Variant, T : HasAndroidTest {
    val runtimeClasspath = runtimeClasspathConfiguration()

    registerLightsaberTask(
      registrar = registrar,
      validateUsage = validateUsage,
      validateUnusedImports = validateUnusedImports,
      validateUnusedImportsVerbose = validateUnusedImportsVerbose,
      dumpDebugReport = dumpDebugReport,
      classpathProvider = classpathProvider(runtimeClasspath),
      modulesClasspathProvider = modulesClasspathProvider(runtimeClasspath),
      buildCacheService = buildCacheService,
    )

    androidTest?.let { androidTest ->
      val androidTestRuntimeClasspath = androidTest.runtimeClasspathConfiguration()

      androidTest.registerLightsaberTask(
        registrar = registrar,
        validateUsage = validateUsage,
        validateUnusedImports = validateUnusedImports,
        validateUnusedImportsVerbose = validateUnusedImportsVerbose,
        dumpDebugReport = dumpDebugReport,
        classpathProvider = classpathProvider(androidTestRuntimeClasspath),
        modulesClasspathProvider = modulesClasspathProvider(androidTestRuntimeClasspath) - modulesClasspathProvider(runtimeClasspath),
        buildCacheService = buildCacheService,
      )
    }
  }

  private fun Component.registerLightsaberTask(
    registrar: TransformTaskRegistrar,
    validateUsage: Provider<Boolean>,
    validateUnusedImports: Provider<Boolean>,
    validateUnusedImportsVerbose: Provider<Boolean>,
    dumpDebugReport: Provider<Boolean>,
    classpathProvider: Provider<FileCollection>,
    modulesClasspathProvider: Provider<FileCollection>,
    buildCacheService: Provider<LightsaberSharedBuildCacheService>,
  ) {
    val taskProvider = project.registerTask<LightsaberTransformTask>(
      LightsaberTransformTask.TASK_PREFIX + name.replaceFirstChar { it.uppercaseChar() }
    )

    registrar.register(this, taskProvider)

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

  @Suppress("DEPRECATION")
  private fun configureTransform(extension: AndroidLightsaberPluginExtension) {
    if (project.android !is AppExtension) {
      return
    }

    val transform = LightsaberTransform(
      extension = extension,
      validateUsageByDefault = Flags.validateUsageByDefault(project),
      validateUnusedImportsByDefault = Flags.validateUnusedImportsByDefault(project),
      validateUnusedImportsVerboseByDefault = Flags.validateUnusedImportsVerboseByDefault(project),
      dumpDebugReportByDefault = Flags.dumpDebugReportByDefault(project),
      reportDirectory = computeReportDirectory().toPath()
    )

    project.android.registerTransform(transform)

    project.afterEvaluate {
      extension.bootClasspath = project.android.bootClasspath
    }
  }

  private fun computeReportDirectory(): File {
    return Paths.get(project.buildDir.path, "reports", "lightsaber").toFile()
  }

  private companion object {
    private val SCOPED_ARTIFACTS_VERSION = AndroidPluginVersion(major = 7, minor = 4, micro = 0)
    private val ALL_CLASSES_TRANSFORM_API_VERSION = AndroidPluginVersion(major = 7, minor = 1, micro = 0)
  }
}
