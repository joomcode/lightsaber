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
import com.joom.lightsaber.processor.ErrorReporterImpl
import com.joom.lightsaber.processor.LightsaberParameters
import com.joom.lightsaber.processor.LightsaberParameters.Companion.RT_PATH
import com.joom.lightsaber.processor.LightsaberProcessor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.streams.toList
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class IntegrationTestRule(
  private val root: String,
  private val testCaseProjectsDir: Path = COMPILED_TEST_CASE_PROJECTS_PATH
) : TestRule {

  private val compiledFilesDirectory = testCaseProjectsDir.resolve("_generated")
  private val processedDirectory = testCaseProjectsDir.resolve("_processed")
  private val classpath = System.getProperty("java.class.path")
  private val classpathWithRuntime = computeClasspath(classpath)
  private val classpathWithAllChildJars = filterDirectoriesAndJars(classpathWithRuntime)

  fun assertValidProject(sourceCodeDir: String) {
    processProject(sourceCodeDir)
  }

  fun assertInvalidProject(sourceCodeDir: String, message: String) {
    val reporterMock = mock<ErrorReporter>()
    processProject(sourceCodeDir, reporterMock, ignoreErrors = false)

    verify(reporterMock).reportError(message)
  }

  private fun processProject(
    sourceCodeDir: String,
    errorReporter: ErrorReporter = ErrorReporterImpl(),
    ignoreErrors: Boolean = false
  ) {
    val compiled = compile(root + File.separator + sourceCodeDir)
    val parameters = LightsaberParameters(
      inputs = listOf(compiled),
      outputs = listOf(processedDirectory),
      bootClasspath = RT_PATH,
      classpath = classpathWithAllChildJars,
      projectName = sourceCodeDir,
      gen = processedDirectory,
      errorReporter = errorReporter
    )

    try {
      LightsaberProcessor(parameters).process()
    } catch (e: Throwable) {
      if (!ignoreErrors) throw e
    } finally {
      cleanDir(compiledFilesDirectory)
      cleanDir(processedDirectory)
    }
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
      "-cp", classpath,
      "-nowarn"
    )
    if (exitCode != ExitCode.OK) {
      throw RuntimeException("Error $exitCode. See stderr for more details")
    }
    return compiledFilesDirectory
  }

  private fun computeClasspath(classpath: String): List<Path> {
    return classpath.split(File.pathSeparator)
      .map(Paths::get)
      .toMutableList()
  }

  private fun filterDirectoriesAndJars(files: List<Path>): List<Path> {
    val result = mutableListOf<Path>()
    for (f in files) {
      if (!f.exists()) continue
      result.add(f)
      if (f.isDirectory()) {
        result.addAll(f.endsWithJar())
      }
    }
    return result
  }

  private fun Path.endsWithJar(): List<Path> {
    return getFilesThat(this) {
      it.extension.endsWith("jar", ignoreCase = true)
    }
  }

  private fun getFilesThat(path: Path, filter: (Path) -> Boolean): List<Path> {
    Files.walk(path).use { paths ->
      return paths
        .filter(filter)
        .toList()
    }
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
        base.evaluate()
      }
    }
  }

  companion object {
    private val BUILD_DIR_PATH = Paths.get(IntegrationTestRule::class.java.classLoader.getResource(".")!!.toURI())
    private val COMPILED_TEST_CASE_PROJECTS_PATH = BUILD_DIR_PATH.resolve("../../../resources/test/")
  }
}
