@file:Suppress("DEPRECATION")

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

package com.joom.lightsaber.plugin

import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.joom.lightsaber.processor.LightsaberParameters
import com.joom.lightsaber.processor.LightsaberProcessor
import com.joom.lightsaber.processor.LightsaberSharedBuildCache
import com.joom.lightsaber.processor.logging.getLogger
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.EnumSet

class LightsaberTransform(
  private val extension: AndroidLightsaberPluginExtension,
  private val validateUsageByDefault: Boolean,
  private val validateUnusedImportsByDefault: Boolean,
  private val validateUnusedImportsVerboseByDefault: Boolean,
  private val dumpDebugReportByDefault: Boolean,
  private val reportDirectory: Path
) : Transform() {
  private val logger = getLogger()

  override fun transform(invocation: TransformInvocation) {
    if (!invocation.isIncremental) {
      invocation.outputProvider.deleteAll()
    }

    val inputs = invocation.inputs.flatMap { it.jarInputs + it.directoryInputs }
    val outputs = inputs.map { input ->
      val format = if (input is JarInput) Format.JAR else Format.DIRECTORY
      invocation.outputProvider.getContentLocation(
        input.name,
        input.contentTypes,
        input.scopes,
        format
      )
    }

    val parameters = LightsaberParameters(
      inputs = inputs.map { it.file.toPath() },
      outputs = outputs.map { it.toPath() },
      gen = invocation.outputProvider.getContentLocation(
        "gen-lightsaber",
        QualifiedContent.DefaultContentType.CLASSES,
        QualifiedContent.Scope.PROJECT,
        Format.DIRECTORY
      ).toPath(),
      classpath = invocation.referencedInputs.flatMap {
        it.jarInputs.map { it.file.toPath() } + it.directoryInputs.map { it.file.toPath() }
      },
      modulesClasspath = emptyList(),
      bootClasspath = extension.bootClasspath.map { it.toPath() },
      projectName = invocation.context.path.replace(":transformClassesWithLightsaberFor", ":").replace(':', '$'),
      validateUsage = extension.validateUsage ?: validateUsageByDefault,
      validateUnusedImports = extension.validateUnusedImports ?: validateUnusedImportsByDefault,
      validateUnusedImportsVerbose = extension.validateUnusedImportsVerbose ?: validateUnusedImportsVerboseByDefault,
      dumpDebugReport = extension.dumpDebugReport ?: dumpDebugReportByDefault,
      reportDirectory = reportDirectory,
      sharedBuildCache = LightsaberSharedBuildCache.create(),
    )
    logger.info("Starting Lightsaber processor: {}", parameters)
    val processor = LightsaberProcessor(parameters)
    try {
      processor.process()
      logger.info("Lightsaber finished processing")
    } catch (exception: IOException) {
      logger.error("Lightsaber failed", exception)
      throw exception
    } catch (exception: Exception) {
      logger.error("Lightsaber failed", exception)
      throw TransformException(exception)
    }
  }

  override fun getName(): String {
    return "lightsaber"
  }

  override fun getInputTypes(): Set<QualifiedContent.ContentType> {
    return EnumSet.of(QualifiedContent.DefaultContentType.CLASSES)
  }

  override fun getParameterInputs(): MutableMap<String, Any> {
    return mutableMapOf(
      "validateUsage" to (extension.validateUsage ?: validateUsageByDefault),
      "validateUnusedImports" to (extension.validateUnusedImports ?: validateUnusedImportsByDefault),
      "validateUnusedImportsVerbose" to (extension.validateUnusedImportsVerbose ?: validateUnusedImportsVerboseByDefault),
      "dumpDebugReport" to (extension.dumpDebugReport ?: dumpDebugReportByDefault),
      "cacheable" to extension.cacheable,
      "bootClasspath" to extension.bootClasspath
        .map { it.absolutePath }
        .sorted()
        .joinToString()
    )
  }

  override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
    return EnumSet.of(
      QualifiedContent.Scope.PROJECT,
      QualifiedContent.Scope.SUB_PROJECTS
    )
  }

  override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope> {
    return EnumSet.of(
      QualifiedContent.Scope.PROJECT,
      QualifiedContent.Scope.SUB_PROJECTS,
      QualifiedContent.Scope.EXTERNAL_LIBRARIES,
      QualifiedContent.Scope.TESTED_CODE,
      QualifiedContent.Scope.PROVIDED_ONLY
    )
  }

  override fun isIncremental(): Boolean {
    return false
  }

  override fun isCacheable(): Boolean {
    return extension.cacheable
  }

  private fun TransformOutputProvider.getContentLocation(
    name: String,
    contentType: QualifiedContent.ContentType,
    scope: QualifiedContent.Scope,
    format: Format
  ): File {
    return getContentLocation(name, setOf(contentType), EnumSet.of(scope), format)
  }
}
