/*
 * Copyright 2023 SIA Joom
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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI

internal class AndroidLightsaberPluginTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private companion object {
    private const val TRANSFORM_TASK = ":lightsaberTransformClassesDebug"

    @Language("xml")
    private const val ANDROID_MANIFEST = """
<?xml version="1.0" encoding="utf-8"?>
<manifest />
"""
  }

  @Test
  fun testBytecodeModeRegistersTransformTask() {
    val projectRoot = createProjectDirectory(TestProcessingMode.BYTECODE)

    val result = createGradleRunner(projectRoot).build()

    val tasks = result.parseDryRunExecution()
    Assert.assertTrue(tasks.any { it.path == TRANSFORM_TASK })
  }

  @Test
  fun testKotlinCompilerPluginModeDoesNotRegisterTransformTask() {
    val projectRoot = createProjectDirectory(TestProcessingMode.KOTLIN_COMPILER_PLUGIN)

    val result = createGradleRunner(projectRoot).build()

    val tasks = result.parseDryRunExecution()
    Assert.assertFalse(tasks.any { it.path == TRANSFORM_TASK })
    Assert.assertTrue(tasks.any { it.path == ":compileDebugKotlin" })
  }

  private fun createProjectDirectory(processingMode: TestProcessingMode): File {
    val projectRoot = temporaryFolder.newFolder()
    writeText(createBuildGradle(processingMode), File(projectRoot, "build.gradle"))
    writeText(ANDROID_MANIFEST, File(projectRoot, "src/main/AndroidManifest.xml"))
    writeText("package com.joom.lightsaber.test\n", File(projectRoot, "src/main/kotlin/Source.kt"))
    return projectRoot
  }

  private fun createGradleRunner(projectDir: File): GradleRunner {
    return GradleRunner.create()
      .withGradleDistribution(URI.create(GradleDistribution.GRADLE_9_5.url))
      .forwardOutput()
      .withProjectDir(projectDir)
      .withArguments("assembleDebug", "--dry-run", "--stacktrace")
  }

  private fun BuildResult.parseDryRunExecution(): List<BuildTask> {
    val split = output.split('\n')
    return split.mapNotNull {
      if (it.startsWith(":")) {
        val (path, _) = it.split(" ")
        TestBuildTask(path = path, outcome = TaskOutcome.SKIPPED)
      } else {
        null
      }
    }
  }

  private fun writeText(content: String, destination: File) {
    if (!destination.parentFile.exists() && !destination.parentFile.mkdirs()) {
      error("Failed to create parent directory ${destination.parentFile}")
    }

    destination.writeText(content)
  }

  @Language("gradle")
  private fun createBuildGradle(processingMode: TestProcessingMode): String {
    val agpVersion = checkNotNull(System.getProperty("android.tools.version"))
    val compileSdk = checkNotNull(System.getProperty("android.compile.sdk.version"))
    val kotlinVersion = checkNotNull(System.getProperty("kotlin.version"))
    val lightsaberVersion = checkNotNull(System.getProperty("lightsaber.version"))
    return """
      buildscript {
        repositories {
          google()
          mavenLocal()
          mavenCentral()
        }

        dependencies {
          classpath "com.android.tools.build:gradle:$agpVersion"
          classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
          classpath "com.joom.lightsaber:lightsaber-gradle-plugin:$lightsaberVersion"
        }
      }

      apply plugin: "com.android.application"
      apply plugin: "com.joom.lightsaber.android"

      lightsaber {
        processingMode = com.joom.lightsaber.plugin.ProcessingMode.${processingMode.name}
      }

      repositories {
        google()
        mavenCentral()
      }

      android {
        compileSdk = $compileSdk

        defaultConfig {
          applicationId "com.joom.lightsaber.test"
          namespace "com.joom.lightsaber.test"
          minSdk = 21
          targetSdk = $compileSdk
          versionCode 1
          versionName "1"
        }
      }
    """.trimIndent()
  }

  private data class TestBuildTask(
    private val path: String,
    private val outcome: TaskOutcome,
  ) : BuildTask {
    override fun getPath(): String {
      return path
    }

    override fun getOutcome(): TaskOutcome {
      return outcome
    }
  }

  private enum class TestProcessingMode {
    BYTECODE,
    KOTLIN_COMPILER_PLUGIN,
  }
}
