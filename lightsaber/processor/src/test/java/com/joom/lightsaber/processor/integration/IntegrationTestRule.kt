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
import com.joom.lightsaber.processor.LightsaberSharedBuildCache
import com.joom.lightsaber.processor.ProcessingException
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
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
  private val reportDirectory = testCaseProjectsDir.resolve("_reports")
  private val classpath = JvmRuntimeUtil.computeRuntimeClasses()

  fun assertValidProject(sourceCodeDir: String) {
    val reporter = TestErrorReporter()
    processProject(sourceCodeDir, reporter)

    reporter.assertNoErrorsReported()
  }

  fun assertInvalidProject(sourceCodeDir: String, message: String) {
    val reporter = TestErrorReporter()
    try {
      processProject(sourceCodeDir, reporter)
    } catch (e: Throwable) {
      // ignore
    }

    reporter.assertErrorReported(message)
  }

  fun compileProject(sourceCodeDir: String, classpath: List<Path> = emptyList()): Path {
    return compile(projectName = sourceCodeDir, sourceCodeDir = root + File.separator + sourceCodeDir, classpath)
  }

  fun processProject(
    sourceCodeDir: String,
    errorReporter: ErrorReporter,
    modules: List<Path> = emptyList(),
    ignoreErrors: Boolean = false,
  ): Path {
    return processProject(
      compiled = compileProject(sourceCodeDir, classpath = modules),
      projectName = sourceCodeDir,
      errorReporter = errorReporter,
      modules = modules,
      ignoreErrors = ignoreErrors,
    )
  }

  fun processProject(
    compiled: Path,
    projectName: String,
    errorReporter: ErrorReporter,
    modules: List<Path> = emptyList(),
    ignoreErrors: Boolean = false,
    validateUsage: Boolean = true,
    validateUnusedImports: Boolean = false,
    dumpDebugReport: Boolean = false,
  ): Path {
    val outputDirectory = processedDirectory.resolve(projectName)
    val reportDirectory = reportDirectory.resolve(projectName)

    val parameters = LightsaberParameters(
      inputs = listOf(compiled),
      outputs = listOf(outputDirectory),
      bootClasspath = classpath,
      modulesClasspath = modules,
      classpath = emptyList(),
      projectName = projectName,
      gen = outputDirectory,
      errorReporter = errorReporter,
      validateUsage = validateUsage,
      validateUnusedImports = validateUnusedImports,
      dumpDebugReport = dumpDebugReport,
      reportDirectory = reportDirectory,
      sharedBuildCache = LightsaberSharedBuildCache.create(),
    )

    try {
      LightsaberProcessor(parameters).process()
    } catch (exception: ProcessingException) {
      if (!ignoreErrors) {
        throw exception
      }
    }

    return outputDirectory
  }

  private fun compile(projectName: String, sourceCodeDir: String, classpath: List<Path> = emptyList()): Path {
    val outputDirectory = compiledFilesDirectory.resolve(projectName)
    cleanDir(outputDirectory)

    val input = testCaseProjectsDir.resolve(sourceCodeDir).toAbsolutePath()

    if (!input.exists()) {
      throw IllegalArgumentException("Cannot find directory $sourceCodeDir in test resources")
    }

    val compiler = K2JVMCompiler()
    // https://kotlinlang.org/docs/compiler-reference.html#common-options

    val errorStream = ByteArrayOutputStream()
    val messageCollector = PrintingMessageCollector(PrintStream(errorStream), MessageRenderer.PLAIN_RELATIVE_PATHS, /* verbose = */false)

    val exitCode = compiler.exec(
      messageCollector,
      Services.EMPTY,
      K2JVMCompilerArguments().apply {
        freeArgs = listOf(input.toString())
        destination = outputDirectory.absolutePathString()
        suppressWarnings = true
        this.classpath = (listOf(JvmRuntimeUtil.JAVA_CLASS_PATH) + classpath.map { it.absolutePathString() }).joinToString(File.pathSeparator)
      }
    )
    if (exitCode != ExitCode.OK) {
      val error = errorStream.toString(Charsets.UTF_8.name())
      throw RuntimeException("Error $exitCode:\n${error}")
    }
    return outputDirectory
  }

  private fun cleanDir(dir: Path) {
    if (!dir.exists()) return
    dir.toFile().deleteRecursively()
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
