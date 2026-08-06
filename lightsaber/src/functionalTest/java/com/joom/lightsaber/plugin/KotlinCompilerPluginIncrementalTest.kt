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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.attribute.FileTime

internal class KotlinCompilerPluginIncrementalTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun testChangingProvidedByTargetInvalidatesConfiguration() {
    val projectRoot = fixtureProject("jvm")

    runner(projectRoot).build()

    val unchangedConfiguration = projectRoot.resolve(
      "build/classes/kotlin/main/com/example/incremental/TestModule.class"
    ).toPath()
    val configurationTimestamp = FileTime.fromMillis(2_000_000)
    Files.setLastModifiedTime(unchangedConfiguration, configurationTimestamp)
    projectRoot.applyStep(
      fixture = "jvm",
      step = "first-target-body-changed.kt",
      target = "src/main/kotlin/com/example/incremental/FirstTarget.kt",
    )

    val bodyChangeResult = runner(projectRoot).build()

    assertTrue(bodyChangeResult.output.contains("Reusing configuration cache."))
    assertEquals(configurationTimestamp, Files.getLastModifiedTime(unchangedConfiguration))

    projectRoot.applyStep(
      fixture = "jvm",
      step = "second-target-with-binding.kt",
      target = "src/main/kotlin/com/example/incremental/SecondTarget.kt",
    )
    projectRoot.applyStep(
      fixture = "jvm",
      step = "expect-second-target.kt",
      target = "src/test/kotlin/com/example/incremental/IncrementalTest.kt",
    )

    runner(projectRoot).build()

    projectRoot.applyStep(
      fixture = "jvm",
      step = "second-target-without-binding.kt",
      target = "src/main/kotlin/com/example/incremental/SecondTarget.kt",
    )
    projectRoot.applyStep(
      fixture = "jvm",
      step = "expect-missing-second-target.kt",
      target = "src/test/kotlin/com/example/incremental/IncrementalTest.kt",
    )

    runner(projectRoot).build()

    val unchangedClass = projectRoot.resolve(
      "build/classes/kotlin/main/com/example/incremental/FirstTarget.class"
    ).toPath()
    val sentinelTimestamp = FileTime.fromMillis(1_000_000)
    Files.setLastModifiedTime(unchangedClass, sentinelTimestamp)
    projectRoot.applyStep(
      fixture = "jvm",
      step = "unrelated-after.kt",
      target = "src/main/kotlin/com/example/incremental/Unrelated.kt",
    )

    runner(projectRoot, "compileKotlin").build()

    assertEquals(sentinelTimestamp, Files.getLastModifiedTime(unchangedClass))

    projectRoot.applyStep(
      fixture = "jvm",
      step = "unrelated-after-again.kt",
      target = "src/main/kotlin/com/example/incremental/Unrelated.kt",
    )
    val result = runner(projectRoot, "compileKotlin").build()

    assertTrue(result.output.contains("Reusing configuration cache."))
    assertEquals(sentinelTimestamp, Files.getLastModifiedTime(unchangedClass))
  }

  @Test
  fun testChangingProvidedByTargetInAndroidJavaSourceDirectoryInvalidatesConfiguration() {
    val projectRoot = fixtureProject("android")

    runner(projectRoot, "testDebugUnitTest").build()

    projectRoot.applyStep(
      fixture = "android",
      step = "second-target-with-binding.kt",
      target = "src/main/java/com/example/incremental/SecondTarget.kt",
    )
    projectRoot.applyStep(
      fixture = "android",
      step = "expect-second-target.kt",
      target = "src/test/java/com/example/incremental/IncrementalTest.kt",
    )

    runner(projectRoot, "testDebugUnitTest").build()
  }

  @Test
  fun testAndroidValidationReadsBuiltInKotlinOutput() {
    val projectRoot = fixtureProject("android")
    projectRoot.applyStep(
      fixture = "android",
      step = "unused-imports.kt",
      target = "src/main/java/com/example/incremental/UnusedImports.kt",
    )

    val result = runner(
      projectRoot,
      "compileDebugKotlin",
      "-Plightsaber.validate.unused.imports=true",
    ).buildAndFail()

    assertTrue(result.output.contains("- Contract com.example.incremental.UnusedContract"))
  }

  @Test
  fun testChangingUpstreamContractRebuildsDownstreamConfiguration() {
    val projectRoot = fixtureProject("cross-module")

    runner(projectRoot, ":impl:test").build()

    val downstreamConfiguration = projectRoot.resolve(
      "impl/build/classes/kotlin/main/com/example/impl/AppConfiguration.class"
    ).toPath()
    val configurationTimestamp = FileTime.fromMillis(3_000_000)
    Files.setLastModifiedTime(downstreamConfiguration, configurationTimestamp)
    projectRoot.applyStep(
      fixture = "cross-module",
      step = "app-contract-body-changed.kt",
      target = "api/src/main/kotlin/com/example/api/AppContract.kt",
    )

    val bodyChangeResult = runner(projectRoot, ":impl:compileKotlin").build()

    assertEquals(TaskOutcome.SUCCESS, bodyChangeResult.task(":api:compileKotlin")?.outcome)
    assertEquals(configurationTimestamp, Files.getLastModifiedTime(downstreamConfiguration))

    projectRoot.applyStep(
      fixture = "cross-module",
      step = "app-contract-after.kt",
      target = "api/src/main/kotlin/com/example/api/AppContract.kt",
    )
    projectRoot.applyStep(
      fixture = "cross-module",
      step = "expect-second-contract-target.kt",
      target = "impl/src/test/kotlin/com/example/impl/IncrementalTest.kt",
    )

    val result = runner(projectRoot, ":impl:test").build()

    assertEquals(TaskOutcome.SUCCESS, result.task(":api:compileKotlin")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, result.task(":impl:compileKotlin")?.outcome)
    assertTrue(configurationTimestamp != Files.getLastModifiedTime(downstreamConfiguration))
  }

  private fun fixtureProject(fixture: String): File {
    val source = fixtureResource("$fixture/project")
    val target = temporaryFolder.newFolder()
    source.copyRecursively(target, overwrite = true)
    target.resolve("build.gradle").replaceTemplateTokens()
    return target
  }

  private fun File.applyStep(fixture: String, step: String, target: String) {
    fixtureResource("$fixture/steps/$step").copyTo(resolve(target), overwrite = true)
  }

  private fun fixtureResource(path: String): File {
    val resource = checkNotNull(javaClass.getResource("$FIXTURES_ROOT/$path")) {
      "Fixture resource does not exist: $path"
    }
    check(resource.protocol == "file") {
      "Fixture resources must be available as files, got: $resource"
    }
    return File(resource.toURI())
  }

  private fun File.replaceTemplateTokens() {
    var content = readText()
    TEMPLATE_PROPERTIES.forEach { (token, property) ->
      if (token in content) {
        content = content.replace(token, checkNotNull(System.getProperty(property)))
      }
    }
    writeText(content)
  }

  private fun runner(
    projectRoot: File,
    task: String = "test",
    vararg additionalArguments: String,
  ): GradleRunner {
    return GradleRunner.create()
      .withGradleDistribution(URI.create(GradleDistribution.GRADLE_9_5.url))
      .withProjectDir(projectRoot)
      .withArguments(
        task,
        "--no-build-cache",
        "--configuration-cache",
        "--stacktrace",
        "-Plightsaber.processing.mode=KOTLIN_COMPILER_PLUGIN",
        *additionalArguments,
      )
      .forwardOutput()
  }

  private companion object {
    private const val FIXTURES_ROOT = "/incremental"

    private val TEMPLATE_PROPERTIES = mapOf(
      "%KOTLIN_VERSION%" to "kotlin.version",
      "%LIGHTSABER_VERSION%" to "lightsaber.version",
      "%ANDROID_TOOLS_VERSION%" to "android.tools.version",
      "%COMPILE_SDK%" to "android.compile.sdk.version",
    )
  }
}
