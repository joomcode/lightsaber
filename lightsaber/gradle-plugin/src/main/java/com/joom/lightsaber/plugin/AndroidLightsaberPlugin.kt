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
import com.android.build.api.artifact.MultipleArtifact
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

abstract class AndroidLightsaberPlugin : BaseLightsaberPlugin() {
  override fun apply(project: Project) {
    super.apply(project)

    if (!project.hasAndroid) {
      throw GradleException("Lightsaber plugin must be applied *AFTER* Android plugin")
    }

    addDependencies(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)

    val extension = project.extensions.create("lightsaber", AndroidLightsaberPluginExtension::class.java)
    val componentsExtension = project.androidComponents

    if (componentsExtension != null && componentsExtension.pluginVersion >= VARIANT_API_REQUIRED_VERSION) {
      logger.info("Registering lightsaber with variant API")

      configureTransformWithComponents(extension, registerBuildCacheService<LightsaberTransformTask>())
    } else {
      logger.info("Registering lightsaber with transform API")

      configureTransform(extension)
    }
  }

  private fun configureTransformWithComponents(extension: AndroidLightsaberPluginExtension, buildCacheService: Provider<LightsaberSharedBuildCacheService>) {
    val validateUsage = project.provider { extension.validateUsage }

    project.applicationAndroidComponents?.apply {
      onVariants { variant ->
        variant.registerLightsaberTask(validateUsage, buildCacheService)
      }
    }

    project.libraryAndroidComponents?.apply {
      onVariants { variant ->
        variant.registerLightsaberTask(validateUsage, buildCacheService)
      }
    }
  }

  private fun <T> T.registerLightsaberTask(
    validateUsage: Provider<Boolean>,
    buildCacheService: Provider<LightsaberSharedBuildCacheService>
  ) where T : Variant, T : HasAndroidTest {
    val runtimeClasspath = runtimeClasspathConfiguration()

    registerLightsaberTask(
      validateUsage = validateUsage,
      classpathProvider = classpathProvider(runtimeClasspath),
      modulesClasspathProvider = modulesClasspathProvider(runtimeClasspath),
      buildCacheService = buildCacheService,
    )

    androidTest?.let { androidTest ->
      val androidTestRuntimeClasspath = androidTest.runtimeClasspathConfiguration()

      androidTest.registerLightsaberTask(
        validateUsage = validateUsage,
        classpathProvider = classpathProvider(androidTestRuntimeClasspath),
        modulesClasspathProvider = modulesClasspathProvider(androidTestRuntimeClasspath) - modulesClasspathProvider(runtimeClasspath),
        buildCacheService = buildCacheService,
      )
    }
  }

  private fun Component.registerLightsaberTask(
    validateUsage: Provider<Boolean>,
    classpathProvider: Provider<FileCollection>,
    modulesClasspathProvider: Provider<FileCollection>,
    buildCacheService: Provider<LightsaberSharedBuildCacheService>,
  ) {
    val taskProvider = project.registerTask<LightsaberTransformTask>(
      LightsaberTransformTask.TASK_PREFIX + name.replaceFirstChar { it.uppercaseChar() }
    )

    @Suppress("UnstableApiUsage")
    artifacts.use(taskProvider)
      .wiredWith(LightsaberTransformTask::inputClasses, LightsaberTransformTask::outputDirectory)
      .toTransform(MultipleArtifact.ALL_CLASSES_DIRS)

    taskProvider.configure { task ->
      task.classpath.setFrom(classpathProvider)
      task.modulesClasspath.setFrom(modulesClasspathProvider)

      @Suppress("UnstableApiUsage")
      task.bootClasspath.from(project.androidComponents!!.sdkComponents.bootClasspath)
      task.sharedBuildCacheService.set(buildCacheService)
      task.validateUsage.set(validateUsage)

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

  private fun configureTransform(extension: AndroidLightsaberPluginExtension) {
    if (project.android !is AppExtension) {
      return
    }

    val transform = LightsaberTransform(extension)
    project.android.registerTransform(transform)

    project.afterEvaluate {
      extension.bootClasspath = project.android.bootClasspath
    }
  }

  private companion object {
    private val VARIANT_API_REQUIRED_VERSION = AndroidPluginVersion(major = 7, minor = 1, micro = 0)
  }
}
