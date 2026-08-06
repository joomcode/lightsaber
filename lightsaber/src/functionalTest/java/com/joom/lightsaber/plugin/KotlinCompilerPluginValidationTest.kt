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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI

internal class KotlinCompilerPluginValidationTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun testUnusedImportValidationReportsContractAndVerboseHelp() {
    val result = runner(fixtureProject("unused-imports"), "compileKotlin").buildAndFail()

    assertTrue(
      result.output.contains(
        "Found unused imports in a contract configuration " +
          "com.example.validation.MainContractConfiguration:"
      )
    )
    assertTrue(result.output.contains("- Contract com.example.validation.UnusedContract"))
    assertTrue(result.output.contains("Unused imports validation can be disabled for the entire project"))
  }

  @Test
  fun testUnusedImportValidationVerboseHelpCanBeDisabled() {
    val result = runner(
      fixtureProject("unused-imports"),
      "compileKotlin",
      "-Plightsaber.validate.unused.imports.verbose=false",
    ).buildAndFail()

    assertTrue(result.output.contains("- Contract com.example.validation.UnusedContract"))
    assertFalse(result.output.contains("Unused imports validation can be disabled for the entire project"))
  }

  @Test
  fun testUnusedImportValidationCanBeDisabled() {
    runner(
      fixtureProject("unused-imports"),
      "compileKotlin",
      "-Plightsaber.validate.unused.imports=false",
    ).build()
  }

  @Test
  fun testDebugReportCanBeDumped() {
    val projectRoot = fixtureProject("unused-imports")

    runner(
      projectRoot,
      "compileKotlin",
      "-Plightsaber.validate.unused.imports=false",
      "-Plightsaber.dump.debug.report=true",
    ).build()

    val report = projectRoot.resolve("build/reports/lightsaber/main/debug-dump.txt")
    assertTrue(report.isFile)
    val reportText = report.readText()
    assertTrue(reportText.contains("Contract Configuration:"))
    assertTrue(reportText.contains("MainContractConfiguration"))
  }

  @Test
  fun testValidationIsSkippedAfterCompilationFailure() {
    val projectRoot = fixtureProject("unused-imports")
    fixtureResource("unused-imports/steps/compilation-error.kt").copyTo(
      projectRoot.resolve("src/main/kotlin/com/example/validation/Configuration.kt"),
      overwrite = true,
    )

    val result = runner(projectRoot, "compileKotlin").buildAndFail()

    assertEquals(TaskOutcome.FAILED, result.task(":compileKotlin")?.outcome)
    val validationOutcome = result.task(":lightsaberValidateCompileKotlin")?.outcome
    assertTrue(validationOutcome == null || validationOutcome == TaskOutcome.SKIPPED)
    assertFalse(result.output.contains("Lightsaber validation failed"))
  }

  @Test
  fun testUsageValidationRejectsUnprocessedProjectDependency() {
    val result = runner(fixtureProject("usage"), ":consumer:compileKotlin").buildAndFail()

    assertTrue(
      result.output.contains(
        "Class com.example.dependency.UnprocessedModule is not processed by lightsaber, " +
          "is plugin applied to module?"
      )
    )
  }

  @Test
  fun testUsageValidationCanBeDisabled() {
    runner(
      fixtureProject("usage"),
      ":consumer:compileKotlin",
      "-Plightsaber.validate.usage=false",
    ).build()
  }

  @Test
  fun testUsageValidationAcceptsProcessedProjectDependency() {
    val projectRoot = fixtureProject("usage")
    fixtureResource("usage/steps/processed-dependency.gradle").copyTo(
      projectRoot.resolve("dependency/build.gradle"),
      overwrite = true,
    )

    runner(projectRoot, ":consumer:compileKotlin").build()
  }

  private fun fixtureProject(fixture: String): File {
    val source = fixtureResource("$fixture/project")
    val target = temporaryFolder.newFolder()
    source.copyRecursively(target, overwrite = true)
    target.resolve("build.gradle").replaceTemplateTokens()
    return target
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
      content = content.replace(token, checkNotNull(System.getProperty(property)))
    }
    writeText(content)
  }

  private fun runner(projectRoot: File, vararg arguments: String): GradleRunner {
    return GradleRunner.create()
      .withGradleDistribution(URI.create(GradleDistribution.GRADLE_9_5.url))
      .withProjectDir(projectRoot)
      .withArguments(
        *arguments,
        "--no-build-cache",
        "--configuration-cache",
        "--stacktrace",
        "-Plightsaber.processing.mode=KOTLIN_COMPILER_PLUGIN",
      )
      .forwardOutput()
  }

  private companion object {
    private const val FIXTURES_ROOT = "/validation"

    private val TEMPLATE_PROPERTIES = mapOf(
      "%KOTLIN_VERSION%" to "kotlin.version",
      "%LIGHTSABER_VERSION%" to "lightsaber.version",
    )
  }
}
