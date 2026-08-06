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

package com.joom.lightsaber.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import java.io.File

const val LIGHTSABER_COMPILER_PLUGIN_ID = "com.joom.lightsaber.compiler"

private val PROFILE_OPTION = CliOption(
  optionName = "profile",
  valueDescription = "compilation label",
  description = "Print Lightsaber IR generation time",
  required = false,
  allowMultipleOccurrences = false,
)
private val PROFILE_KEY = CompilerConfigurationKey<String>("Lightsaber profile label")
private val GRAPH_FINGERPRINT_OPTION = CliOption(
  optionName = "graphFingerprint",
  valueDescription = "SHA-256",
  description = "Fingerprint of source files that may affect the aggregated Lightsaber graph",
  required = false,
  allowMultipleOccurrences = false,
)

@OptIn(ExperimentalCompilerApi::class)
class LightsaberCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = LIGHTSABER_COMPILER_PLUGIN_ID
  override val pluginOptions: Collection<AbstractCliOption> = listOf(
    PROFILE_OPTION,
    GRAPH_FINGERPRINT_OPTION,
  )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration,
  ) {
    when (option.optionName) {
      PROFILE_OPTION.optionName -> configuration.put(PROFILE_KEY, value)
      GRAPH_FINGERPRINT_OPTION.optionName -> Unit
      else -> error("Unexpected Lightsaber compiler plugin option: ${option.optionName}")
    }
  }
}

@OptIn(ExperimentalCompilerApi::class)
class LightsaberCompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val pluginId: String = LIGHTSABER_COMPILER_PLUGIN_ID
  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    // KAPT runs a separate compiler invocation to produce Java stubs. The real
    // Kotlin compilation runs after KAPT and is the only output that Lightsaber
    // must process.
    if (!configuration.isKaptStubCompilation()) {
      val profile = configuration.get(PROFILE_KEY)?.let(Profile::parse)
      IrGenerationExtension.registerExtension(
        LightsaberNativeIrGenerationExtension(
          profileLabel = profile?.label,
        ) { message ->
          profile?.output?.let { output ->
            output.parentFile.mkdirs()
            output.writeText(message)
          }
        }
      )
    }
  }
}

private data class Profile(val label: String, val output: File) {
  companion object {
    fun parse(value: String): Profile {
      val separator = value.indexOf('|')
      require(separator > 0) { "Invalid Lightsaber profile configuration: $value" }
      return Profile(value.substring(0, separator), File(value.substring(separator + 1)))
    }
  }
}

private fun CompilerConfiguration.isKaptStubCompilation(): Boolean {
  val outputPath = get(JVMConfigurationKeys.OUTPUT_DIRECTORY)?.path.orEmpty()
  if (outputPath.contains("kapt", ignoreCase = true)) {
    return true
  }

  return sequenceOf(
    Thread.currentThread().contextClassLoader,
    LightsaberCompilerPluginRegistrar::class.java.classLoader,
  ).filterNotNull().any { classLoader ->
    runCatching {
      val pluginClass = Class.forName("org.jetbrains.kotlin.kapt.KaptPluginKt", false, classLoader)
      @Suppress("UNCHECKED_CAST")
      val key = pluginClass.getMethod("getKAPT_OPTIONS").invoke(null) as CompilerConfigurationKey<Any>
      get(key) != null
    }.getOrDefault(false)
  }
}
