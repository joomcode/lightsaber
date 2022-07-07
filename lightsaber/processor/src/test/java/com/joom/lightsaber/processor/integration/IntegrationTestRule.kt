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

package com.joom.lightsaber.processor.integration

import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.JvmRuntimeUtil
import com.joom.lightsaber.processor.LightsaberParameters
import com.joom.lightsaber.processor.LightsaberProcessor
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class IntegrationTestRule(
  private val root: String,
  private val testCaseProjectsDir: Path = COMPILED_TEST_CASE_PROJECTS_PATH
) : TestRule {

  private val compiledFilesDirectory = testCaseProjectsDir.resolve("_generated")
  private val processedDirectory = testCaseProjectsDir.resolve("_processed")
  private val classpath = JvmRuntimeUtil.computeRuntimeClasses()

  fun assertValidProject(sourceCodeDir: String) {
    val reporter = TestErrorReporter()
    processProject(sourceCodeDir, reporter)

    reporter.assertNoErrorsReported()
  }

  fun assertInvalidProject(sourceCodeDir: String, message: String) {
    val reporter = TestErrorReporter()
    processProject(sourceCodeDir, reporter)

    reporter.assertErrorReported(message)
  }

  fun processProject(
    sourceCodeDir: String,
    errorReporter: ErrorReporter,
    modules: List<Path> = emptyList(),
  ): Path {
    val compiled = compile(root + File.separator + sourceCodeDir)

    val parameters = LightsaberParameters(
      inputs = listOf(compiled),
      outputs = listOf(processedDirectory),
      bootClasspath = classpath,
      modulesClasspath = modules,
      classpath = emptyList(),
      projectName = sourceCodeDir,
      gen = processedDirectory,
      errorReporter = errorReporter
    )

    LightsaberProcessor(parameters).process()

    return processedDirectory
  }

  private fun compile(sourceCodeDir: String): Path {
    cleanDir(compiledFilesDirectory)

    val input = testCaseProjectsDir.resolve(sourceCodeDir).toAbsolutePath()

    if (!input.exists()) {
      throw IllegalArgumentException("Cannot find directory $sourceCodeDir in test resources")
    }

    val compiler = K2JVMCompiler()
    // https://kotlinlang.org/docs/compiler-reference.html#common-options

    val exitCode = compiler.exec(
      System.err,
      input.toString(),
      "-d", compiledFilesDirectory.absolutePathString(),
      "-cp", JvmRuntimeUtil.JAVA_CLASS_PATH,
      "-nowarn"
    )
    if (exitCode != ExitCode.OK) {
      throw RuntimeException("Error $exitCode. See stderr for more details")
    }
    return compiledFilesDirectory
  }

  private fun cleanDir(dir: Path) {
    if (!dir.exists()) return
    Files.walk(dir).use { paths ->
      paths
        .sorted(Comparator.reverseOrder())
        .map { it.toFile() }
        .forEach(File::delete)

    }
  }

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {

        try {
          base.evaluate()
        } finally {
          after()
        }
      }
    }
  }

  private fun after() {
    cleanDir(compiledFilesDirectory)
    cleanDir(processedDirectory)
  }

  companion object {
    private val BUILD_DIR_PATH = Paths.get(IntegrationTestRule::class.java.classLoader.getResource(".")!!.toURI())
    private val COMPILED_TEST_CASE_PROJECTS_PATH = BUILD_DIR_PATH.resolve("../../../resources/test/")
  }
}
