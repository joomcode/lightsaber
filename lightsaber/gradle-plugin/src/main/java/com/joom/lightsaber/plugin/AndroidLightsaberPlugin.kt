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
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

abstract class AndroidLightsaberPlugin : BaseLightsaberPlugin(), KotlinCompilerPluginSupportPlugin {
  private val compilerPluginComponents = mutableMapOf<String, Component>()

  @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
  override fun apply(target: Project) {
    super<BaseLightsaberPlugin>.apply(target)
    val project = target

    val androidComponents = project.androidComponents
      ?: throw GradleException("Lightsaber plugin must be applied *AFTER* Android plugin")

    if (androidComponents.pluginVersion < MIN_AGP_VERSION) {
      throw GradleException(
        "Lightsaber Android plugin requires Android Gradle Plugin $MIN_AGP_VERSION or newer, " +
          "but ${androidComponents.pluginVersion} is used"
      )
    }

    addDependencies(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)

    val extension = project.extensions.create("lightsaber", AndroidLightsaberPluginExtension::class.java).apply {
      processingMode = Flags.processingModeByDefault(project)
    }
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
      components = androidComponents,
      extension = extension,
      validateUsage = validateUsage,
      validateUnusedImports = validateUnusedImports,
      validateUnusedImportsVerbose = validateUnusedImportsVerbose,
      dumpDebugReport = dumpDebugReport,
      buildCacheService = buildCacheService,
    )
  }

  private fun configureVariants(
    components: AndroidComponentsExtension<*, *, *>,
    extension: AndroidLightsaberPluginExtension,
    validateUsage: Provider<Boolean>,
    validateUnusedImports: Provider<Boolean>,
    validateUnusedImportsVerbose: Provider<Boolean>,
    dumpDebugReport: Provider<Boolean>,
    buildCacheService: Provider<LightsaberSharedBuildCacheService>,
  ) {
    components.onVariants(components.selector().all()) { variant ->
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
    when (extension.processingMode) {
      ProcessingMode.BYTECODE -> {
        val runtimeClasspath = runtimeClasspathConfiguration()
        val classpath = classpathProvider(runtimeClasspath)
        val modulesClasspath = modulesClasspathProvider(runtimeClasspath)
        registerLightsaberTask(
          validateUsage = validateUsage,
          validateUnusedImports = validateUnusedImports,
          validateUnusedImportsVerbose = validateUnusedImportsVerbose,
          dumpDebugReport = dumpDebugReport,
          classpathProvider = classpath,
          modulesClasspathProvider = modulesClasspath,
          buildCacheService = buildCacheService,
          cacheable = extension.cacheable,
        )

        if (this is HasAndroidTest) {
          val androidTestComponent = androidTest ?: return
          val androidTestRuntimeClasspath = androidTestComponent.runtimeClasspathConfiguration()
          androidTestComponent.registerLightsaberTask(
            validateUsage = validateUsage,
            validateUnusedImports = validateUnusedImports,
            validateUnusedImportsVerbose = validateUnusedImportsVerbose,
            dumpDebugReport = dumpDebugReport,
            classpathProvider = classpathProvider(androidTestRuntimeClasspath),
            modulesClasspathProvider = modulesClasspathProvider(androidTestRuntimeClasspath) - modulesClasspath,
            buildCacheService = buildCacheService,
            cacheable = extension.cacheable,
          )
        }
      }

      ProcessingMode.KOTLIN_COMPILER_PLUGIN -> {
        compilerPluginComponents[name] = this
        if (this is HasAndroidTest) {
          androidTest?.let { compilerPluginComponents[it.name] = it }
        }
      }
    }
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
    val extension = kotlinCompilation.target.project.extensions.findByType(AndroidLightsaberPluginExtension::class.java)
    return extension?.processingMode == ProcessingMode.KOTLIN_COMPILER_PLUGIN &&
      kotlinCompilation.name in compilerPluginComponents
  }

  override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
    val extension = checkNotNull(
      kotlinCompilation.target.project.extensions.findByType(AndroidLightsaberPluginExtension::class.java)
    )
    val component = checkNotNull(compilerPluginComponents[kotlinCompilation.name]) {
      "Lightsaber compiler plugin configuration is missing for ${kotlinCompilation.name}"
    }
    val validationTask = project.registerLightsaberCompilerValidation(
      compilation = kotlinCompilation,
      includeAndroidJavaSourceDirectories = true,
      bootClasspath = project.files(checkNotNull(project.androidComponents).sdkComponents.bootClasspath),
      validateUsage = project.provider { extension.validateUsage ?: Flags.validateUsageByDefault(project) },
      validateUnusedImports = project.provider {
        extension.validateUnusedImports ?: Flags.validateUnusedImportsByDefault(project)
      },
      validateUnusedImportsVerbose = project.provider {
        extension.validateUnusedImportsVerbose ?: Flags.validateUnusedImportsVerboseByDefault(project)
      },
      dumpDebugReport = project.provider {
        extension.dumpDebugReport ?: Flags.dumpDebugReportByDefault(project)
      },
    )
    component.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
      .use(validationTask)
      .toGet(
        ScopedArtifact.CLASSES,
        LightsaberCompilerValidationTask::inputJars,
        LightsaberCompilerValidationTask::inputDirectories,
      )
    return project.provider {
      project.lightsaberCompilerPluginOptions(
        compilation = kotlinCompilation,
        includeAndroidJavaSourceDirectories = true,
      )
    }
  }

  override fun getCompilerPluginId(): String = LIGHTSABER_COMPILER_PLUGIN_ID

  override fun getPluginArtifact(): SubpluginArtifact {
    return SubpluginArtifact(
      groupId = "com.joom.lightsaber",
      artifactId = "lightsaber-compiler-plugin",
      version = Build.VERSION,
    )
  }

  private fun Component.registerLightsaberTask(
    validateUsage: Provider<Boolean>,
    validateUnusedImports: Provider<Boolean>,
    validateUnusedImportsVerbose: Provider<Boolean>,
    dumpDebugReport: Provider<Boolean>,
    classpathProvider: FileCollection,
    modulesClasspathProvider: FileCollection,
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

  private fun Component.runtimeClasspathConfiguration(): Configuration {
    return project.configurations.getByName(name + "RuntimeClasspath")
  }

  private fun classpathProvider(configuration: Configuration): FileCollection {
    return configuration.incomingAndroidJarArtifacts().artifactFiles
  }

  private fun modulesClasspathProvider(configuration: Configuration): FileCollection {
    return configuration.incomingAndroidJarArtifacts { it is ProjectComponentIdentifier }.artifactFiles
  }

  private operator fun FileCollection.minus(other: FileCollection): FileCollection {
    return this - other
  }

  private companion object {
    private val MIN_AGP_VERSION = AndroidPluginVersion(major = 7, minor = 4, micro = 0)
    private const val LIGHTSABER_COMPILER_PLUGIN_ID = "com.joom.lightsaber.compiler"
  }

}
