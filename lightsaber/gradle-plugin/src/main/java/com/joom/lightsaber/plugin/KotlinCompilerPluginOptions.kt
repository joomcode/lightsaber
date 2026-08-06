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

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal fun Project.lightsaberCompilerPluginOptions(
  compilation: KotlinCompilation<*>,
  includeAndroidJavaSourceDirectories: Boolean = false,
): List<SubpluginOption> {
  val localKotlinSources = files(compilation.allKotlinSourceSets.map { it.allKotlinSources })
  if (includeAndroidJavaSourceDirectories) {
    // AGP treats Kotlin under src/*/java as Kotlin compiler inputs, but those directories are not
    // necessarily exposed by KotlinSourceSet.allKotlinSources.
    localKotlinSources.from(
      fileTree("src/main/java") { tree -> tree.include("**/*.kt", "**/*.kts") },
      compilation.allKotlinSourceSets.map { sourceSet ->
        fileTree("src/${sourceSet.name}/java") { tree -> tree.include("**/*.kt", "**/*.kts") }
      }
    )
  }
  val referencedPackages = providers.of(LightsaberReferencedPackagesValueSource::class.java) { spec ->
    spec.parameters.sourceFiles.from(localKotlinSources)
  }
  val compileConfiguration = configurations.getByName(compilation.compileDependencyConfigurationName)
  val dependencyKotlinSources = providers.provider {
    val packages = referencedPackages.get()
    val projectDirectoriesByPath = rootProject.allprojects.associate { it.path to it.projectDir }
    val pending = ArrayDeque(
      compileConfiguration.allDependencies.withType(ProjectDependency::class.java).map(ProjectDependency::getPath)
    )
    val dependencyProjectPaths = mutableSetOf<String>()
    while (pending.isNotEmpty()) {
      val projectPath = pending.removeFirst()
      if (projectPath == path || !dependencyProjectPaths.add(projectPath)) continue
      val dependencyProject = rootProject.project(projectPath)
      dependencyProject.configurations
        .filter { configuration ->
          val name = configuration.name.lowercase()
          name.endsWith("compileclasspath") && "test" !in name && "lint" !in name
        }
        .flatMap { configuration ->
          configuration.allDependencies.withType(ProjectDependency::class.java).map(ProjectDependency::getPath)
        }
        .forEach(pending::addLast)
    }
    dependencyProjectPaths
      .flatMap { projectPath ->
        projectDirectoriesByPath.getValue(projectPath).resolve("src")
          .listFiles()
          .orEmpty()
          .asSequence()
          .filter(File::isDirectory)
          .filterNot { sourceSet -> "test" in sourceSet.name.lowercase() }
          .flatMap { sourceSet ->
            sequenceOf(sourceSet.resolve("kotlin"), sourceSet.resolve("java"))
          }
          .flatMap { languageRoot ->
            packages.asSequence().flatMap { packageName ->
              languageRoot.resolve(packageName.replace('.', File.separatorChar))
                .walkTopDown()
                .filter(File::isFile)
                .filter { it.extension == "kt" || it.extension == "kts" }
            }
          }
          .distinctBy(File::getAbsolutePath)
          .toList()
      }
  }
  val graphFingerprint = providers.of(LightsaberGraphFingerprintValueSource::class.java) { spec ->
    spec.parameters.sourceFiles.from(localKotlinSources)
    spec.parameters.dependencySourceFiles.from(dependencyKotlinSources)
    spec.parameters.projectDirectory.set(layout.projectDirectory)
  }
  val options = mutableListOf(
    SubpluginOption(
      "graphFingerprint",
      lazy { graphFingerprint.get() },
    ),
  )
  if (providers.gradleProperty("lightsaber.profile").orNull.toBoolean()) {
    val label = "$path:${compilation.name}"
    val fileName = "${path.replace(':', '_')}-${compilation.name}.txt"
    val output = rootProject.layout.buildDirectory
      .file("reports/lightsaber-profile/$fileName")
      .get().asFile.absolutePath
    options += SubpluginOption("profile", "$label|$output")
  }
  return options
}

internal abstract class LightsaberReferencedPackagesValueSource :
  ValueSource<Set<String>, LightsaberReferencedPackagesValueSource.Parameters> {

  interface Parameters : ValueSourceParameters {
    val sourceFiles: ConfigurableFileCollection
  }

  override fun obtain(): Set<String> {
    return parameters.sourceFiles.files
      .asSequence()
      .filter(File::isFile)
      .filter { it.extension == "kt" || it.extension == "kts" }
      .flatMap { file ->
        val source = file.readText()
        if (source.kotlinStructureOrNull(requireLightsaberMarker = true) == null) {
          emptySequence()
        } else {
          source.kotlinReferencedPackages().asSequence()
        }
      }
      .toSet()
  }
}

internal abstract class LightsaberGraphFingerprintValueSource :
  ValueSource<String, LightsaberGraphFingerprintValueSource.Parameters> {

  interface Parameters : ValueSourceParameters {
    val sourceFiles: ConfigurableFileCollection
    val dependencySourceFiles: ConfigurableFileCollection
    val projectDirectory: DirectoryProperty
  }

  override fun obtain(): String {
    return lightsaberGraphFingerprint(
      sourceFiles = parameters.sourceFiles,
      dependencySourceFiles = parameters.dependencySourceFiles,
      projectDir = parameters.projectDirectory.get().asFile,
    )
  }
}

/**
 * Lightsaber generates one aggregated injector configurator for a compilation. Kotlin's regular
 * incremental compilation cannot infer that changing a binding in one file also requires the file
 * containing the configurator to be regenerated. Making the graph fingerprint a compiler argument
 * gives Kotlin a precise reason to rebuild the compilation when the DI graph may have changed.
 */
private fun lightsaberGraphFingerprint(
  sourceFiles: FileCollection,
  dependencySourceFiles: FileCollection,
  projectDir: File,
): String {
  val digest = MessageDigest.getInstance("SHA-256")
  sourceFiles.files
    .asSequence()
    .filter(File::isFile)
    .filter { it.extension == "kt" || it.extension == "kts" }
    .distinctBy(File::getAbsolutePath)
    .mapNotNull { file ->
      file.readText().kotlinStructureOrNull(requireLightsaberMarker = true)?.let { structure -> file to structure }
    }
    .sortedBy { (file, _) -> file.absolutePath }
    .forEach { (file, structure) ->
      digest.update(file.relativeToOrSelf(projectDir).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
      digest.update(0)
      digest.update(structure.toByteArray(Charsets.UTF_8))
      digest.update(0)
    }
  digest.update(1)
  dependencySourceFiles.files
    .asSequence()
    .filter(File::isFile)
    .filter { it.extension == "kt" || it.extension == "kts" }
    .map { it.readText().kotlinStructureOrNull(requireLightsaberMarker = false).orEmpty() }
    .sorted()
    .forEach { structure ->
      digest.update(structure.toByteArray(Charsets.UTF_8))
      digest.update(0)
    }
  return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}

/**
 * Kotlin may incrementally recompile a configuration because an upstream ABI changed while omitting
 * unchanged local bindings from the compiler invocation. The IR plugin cannot safely aggregate that
 * partial view. Including structural Kotlin source fingerprints from project dependencies in the
 * compiler argument turns such changes into a full downstream compilation, without reacting to
 * method body-only changes in dependencies.
 */
private fun String.kotlinStructureOrNull(requireLightsaberMarker: Boolean): String? {
  val tokens = kotlinTokens()
  val commentFreeSource = tokens.joinToString(separator = "") { it.text }
  if (requireLightsaberMarker && LIGHTSABER_GRAPH_MARKERS.none(commentFreeSource::contains)) {
    return null
  }

  val structure = StringBuilder()
  var declarationKind = DeclarationKind.NONE
  var parentheses = 0
  var brackets = 0
  var index = 0

  fun append(token: KotlinToken) {
    if (token.kind != KotlinTokenKind.NEWLINE) {
      structure.append(token.text.length).append(':').append(token.text)
    }
  }

  while (index < tokens.size) {
    val token = tokens[index]
    if (token.kind == KotlinTokenKind.IDENTIFIER && parentheses == 0 && brackets == 0) {
      declarationKind = when (token.text) {
        "class", "interface", "object" -> DeclarationKind.TYPE
        "fun", "constructor", "init", "get", "set" -> DeclarationKind.EXECUTABLE
        "val", "var" -> DeclarationKind.PROPERTY
        else -> declarationKind
      }
    }

    when (token.text) {
      "(" -> parentheses++
      ")" -> parentheses--
      "[" -> brackets++
      "]" -> brackets--
      "{" -> {
        if (parentheses > 0 || brackets > 0 || declarationKind != DeclarationKind.TYPE) {
          append(token)
          structure.append("0:")
          val closingBrace = tokens.closingBraceAfter(index)
          append(tokens[closingBrace])
          index = closingBrace
          if (parentheses == 0 && brackets == 0) {
            declarationKind = DeclarationKind.NONE
          }
          index++
          continue
        } else {
          declarationKind = DeclarationKind.NONE
        }
      }

      "}" -> declarationKind = DeclarationKind.NONE
      "=" -> if (
        parentheses == 0 &&
        brackets == 0 &&
        (declarationKind == DeclarationKind.EXECUTABLE || declarationKind == DeclarationKind.PROPERTY)
      ) {
        append(token)
        structure.append("0:")
        index = tokens.expressionEndAfter(index)
        declarationKind = DeclarationKind.NONE
        index++
        continue
      }
    }

    append(token)
    index++
  }
  return structure.toString()
}

private fun String.kotlinReferencedPackages(): Set<String> {
  val tokens = kotlinTokens()
  val packages = mutableSetOf<String>()
  var index = 0
  while (index < tokens.size) {
    val keyword = tokens[index]
    if (keyword.kind != KotlinTokenKind.IDENTIFIER || keyword.text !in setOf("package", "import")) {
      index++
      continue
    }

    val isImport = keyword.text == "import"
    var isStarImport = false
    val segments = mutableListOf<String>()
    index++
    while (index < tokens.size && tokens[index].kind != KotlinTokenKind.NEWLINE) {
      val token = tokens[index]
      if (token.kind == KotlinTokenKind.IDENTIFIER) {
        if (token.text == "as") break
        segments += token.text.removeSurrounding("`")
      } else if (token.text == "*") {
        isStarImport = true
        break
      }
      index++
    }
    if (isImport && !isStarImport && segments.isNotEmpty()) {
      segments.removeLast()
    }
    if (segments.isNotEmpty()) {
      packages += segments.joinToString(".")
    }
  }
  return packages
}

private fun List<KotlinToken>.closingBraceAfter(openingBrace: Int): Int {
  var depth = 1
  for (index in openingBrace + 1 until size) {
    when (this[index].text) {
      "{" -> depth++
      "}" -> if (--depth == 0) return index
    }
  }
  return lastIndex
}

private fun List<KotlinToken>.expressionEndAfter(equals: Int): Int {
  var parentheses = 0
  var brackets = 0
  var braces = 0
  for (index in equals + 1 until size) {
    val token = this[index]
    when (token.text) {
      "(" -> parentheses++
      ")" -> parentheses--
      "[" -> brackets++
      "]" -> brackets--
      "{" -> braces++
      "}" -> {
        if (braces == 0 && parentheses == 0 && brackets == 0) return index - 1
        braces--
      }

      ";" -> if (parentheses == 0 && brackets == 0 && braces == 0) return index
    }
    if (token.kind == KotlinTokenKind.NEWLINE && parentheses == 0 && brackets == 0 && braces == 0) {
      return index
    }
  }
  return lastIndex
}

private fun String.kotlinTokens(): List<KotlinToken> = buildList {
  var index = 0
  while (index < length) {
    val start = index
    when {
      this@kotlinTokens[index] == '\n' || this@kotlinTokens[index] == '\r' -> {
        if (this@kotlinTokens[index] == '\r' && this@kotlinTokens.getOrNull(index + 1) == '\n') index++
        add(KotlinToken(KotlinTokenKind.NEWLINE, "\n"))
        index++
      }

      this@kotlinTokens[index].isWhitespace() -> index++
      startsWith("//", index) -> {
        index = indexOf('\n', index).takeIf { it >= 0 } ?: length
      }

      startsWith("/*", index) -> {
        var depth = 1
        index += 2
        while (index < length && depth > 0) {
          when {
            startsWith("/*", index) -> {
              depth++
              index += 2
            }

            startsWith("*/", index) -> {
              depth--
              index += 2
            }

            else -> index++
          }
        }
      }

      startsWith("\"\"\"", index) -> {
        index = indexOf("\"\"\"", index + 3).let { if (it >= 0) it + 3 else length }
        add(KotlinToken(KotlinTokenKind.LITERAL, substring(start, index)))
      }

      this@kotlinTokens[index] == '"' || this@kotlinTokens[index] == '\'' -> {
        val quote = this@kotlinTokens[index++]
        while (index < length) {
          when (this@kotlinTokens[index++]) {
            '\\' -> if (index < length) index++
            quote -> break
          }
        }
        add(KotlinToken(KotlinTokenKind.LITERAL, substring(start, index)))
      }

      this@kotlinTokens[index] == '`' -> {
        index = indexOf('`', index + 1).let { if (it >= 0) it + 1 else length }
        add(KotlinToken(KotlinTokenKind.IDENTIFIER, substring(start, index)))
      }

      this@kotlinTokens[index].isLetter() || this@kotlinTokens[index] == '_' -> {
        index++
        while (index < length && (this@kotlinTokens[index].isLetterOrDigit() || this@kotlinTokens[index] == '_')) {
          index++
        }
        add(KotlinToken(KotlinTokenKind.IDENTIFIER, substring(start, index)))
      }

      this@kotlinTokens[index].isDigit() -> {
        index++
        while (index < length && (this@kotlinTokens[index].isLetterOrDigit() || this@kotlinTokens[index] in "._")) {
          index++
        }
        add(KotlinToken(KotlinTokenKind.LITERAL, substring(start, index)))
      }

      else -> {
        index++
        add(KotlinToken(KotlinTokenKind.SYMBOL, substring(start, index)))
      }
    }
  }
}

private enum class DeclarationKind {
  NONE,
  TYPE,
  EXECUTABLE,
  PROPERTY,
}

private data class KotlinToken(
  val kind: KotlinTokenKind,
  val text: String,
)

private enum class KotlinTokenKind {
  IDENTIFIER,
  LITERAL,
  SYMBOL,
  NEWLINE,
}

private val LIGHTSABER_GRAPH_MARKERS = listOf(
  "com.joom.lightsaber",
  "@ProvidedBy",
  "@ProvidedAs",
  "@ImportedBy",
  "@Provide",
  "@Import",
  "@Contract",
  "@Component",
  "@Module",
  "@Factory",
  "@Eager",
)
