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

package com.joom.lightsaber.processor

import com.joom.grip.CombinedGripFactory
import com.joom.grip.GripFactory
import com.joom.lightsaber.processor.analysis.Analyzer
import com.joom.lightsaber.processor.commons.closeQuietly
import com.joom.lightsaber.processor.commons.getDescription
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.validation.DependencyResolverFactory
import com.joom.lightsaber.processor.validation.UnusedImportsCalculator
import com.joom.lightsaber.processor.validation.UsageValidator
import java.io.File
import java.nio.file.Path

/** Runs the validations that remain optional when Lightsaber generation happens in Kotlin IR. */
object LightsaberValidator {
  fun validate(
    inputs: Collection<Path>,
    classpath: Collection<Path>,
    modulesClasspath: Collection<Path>,
    bootClasspath: Collection<Path>,
    projectName: String,
    validateUsage: Boolean,
    validateUnusedImports: Boolean,
    validateUnusedImportsVerbose: Boolean,
    debugReport: File?,
    sharedBuildCache: LightsaberSharedBuildCache,
  ): List<String> {
    val errorReporter = ErrorReporterImpl()
    val inputsGrip = GripFactory.INSTANCE.create(inputs)
    val grip = CombinedGripFactory.INSTANCE.create(
      listOf(inputsGrip) + CachedGripFactory.create(
        sharedBuildCache,
        classpath + modulesClasspath + bootClasspath,
      )
    )

    try {
      val context = if (validateUnusedImports || debugReport != null) {
        Analyzer(grip, errorReporter, projectName).analyze(inputs)
      } else {
        null
      }

      if (debugReport != null) {
        debugReport.parentFile.mkdirs()
        FileDumpContext(debugReport).use { dumpContext ->
          DebugReport.dump(checkNotNull(context), dumpContext)
        }
      }

      if (validateUnusedImports) {
        checkNotNull(context)
        val unusedImportsCalculator = UnusedImportsCalculator(
          DependencyResolverFactory(
            injectionContext = context,
            includeAllDependenciesInGraph = true,
          )
        )
        context.contractConfigurations.forEach { contractConfiguration ->
          val unusedImports = unusedImportsCalculator.findUnusedImports(contractConfiguration)
          if (unusedImports.isNotEmpty()) {
            errorReporter.reportError {
              append(
                "Found unused imports in a contract configuration " +
                  "${contractConfiguration.type.getDescription()}:"
              )
              unusedImports.forEach { import ->
                appendLine()
                when (import) {
                  is Import.Contract -> append("  - Contract ${import.contract.type.getDescription()}")
                  is Import.Module -> append("  - Module ${import.module.type.getDescription()}")
                }
              }
              if (validateUnusedImportsVerbose) {
                appendLine()
                appendLine()
                appendLine(UNUSED_IMPORTS_HELP)
              }
            }
          }
        }
      }

      if (validateUsage) {
        UsageValidator(grip, errorReporter).validateUsage(modulesClasspath)
      }

      return errorReporter.errors
    } finally {
      inputsGrip.closeQuietly()
    }
  }

  private val UNUSED_IMPORTS_HELP =
    """
      Unused imports validation can be disabled for the entire project by putting "lightsaber.validate.unused.imports=false" to a root gradle.properties file.

      You can also disable it for a particular gradle module by putting the following code to a corresponding build.gradle:
      lightsaber {
        validateUnusedImports = false
      }

      In case you already know how unused imports validation works and never want to see this message again, just put "lightsaber.validate.unused.imports.verbose=false" to a root gradle.properties file.
    """.trimIndent()
}
