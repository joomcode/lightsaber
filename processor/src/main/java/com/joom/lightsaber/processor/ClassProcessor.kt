/*
 * Copyright 2020 SIA Joom
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

import com.joom.lightsaber.processor.analysis.Analyzer
import com.joom.lightsaber.processor.commons.StandaloneClassWriter
import com.joom.lightsaber.processor.commons.closeQuietly
import com.joom.lightsaber.processor.commons.exhaustive
import com.joom.lightsaber.processor.generation.GenerationContextFactory
import com.joom.lightsaber.processor.generation.Generator
import com.joom.lightsaber.processor.generation.model.GenerationContext
import com.joom.lightsaber.processor.generation.model.ProviderFactoryImpl
import com.joom.lightsaber.processor.injection.Patcher
import com.joom.lightsaber.processor.io.DirectoryFileSink
import com.joom.lightsaber.processor.io.FileSource
import com.joom.lightsaber.processor.io.IoFactory
import com.joom.lightsaber.processor.logging.getLogger
import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractConfiguration
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import com.joom.lightsaber.processor.validation.DependencyResolverFactory
import com.joom.lightsaber.processor.validation.Validator
import io.michaelrocks.grip.Grip
import io.michaelrocks.grip.GripFactory
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.Closeable
import java.io.File

class ClassProcessor(
  private val inputs: List<File>,
  private val outputs: List<File>,
  private val genPath: File,
  private val projectName: String,
  classpath: List<File>,
  bootClasspath: List<File>
) : Closeable {

  private val logger = getLogger()

  private val grip: Grip = GripFactory.create(inputs + classpath + bootClasspath)
  private val errorReporter = ErrorReporter()

  private val fileSourcesAndSinks = inputs.zip(outputs) { input, output ->
    val source = IoFactory.createFileSource(input)
    val sink = IoFactory.createFileSink(input, output)
    source to sink
  }
  private val classSink = DirectoryFileSink(genPath)

  fun processClasses() {
    val injectionContext = performAnalysisAndValidation()
    val providerFactory = ProviderFactoryImpl(grip.fileRegistry, projectName)
    val generationContextFactory = GenerationContextFactory(grip.fileRegistry, grip.classRegistry, providerFactory, projectName)
    val generationContext = generationContextFactory.createGenerationContext(injectionContext)
    injectionContext.dump()
    copyAndPatchClasses(injectionContext, generationContext)
    performGeneration(injectionContext, generationContext)
  }

  override fun close() {
    classSink.closeQuietly()

    fileSourcesAndSinks.forEach {
      it.first.closeQuietly()
      it.second.closeQuietly()
    }
  }

  private fun performAnalysisAndValidation(): InjectionContext {
    val analyzer = Analyzer(grip, errorReporter, projectName)
    val context = analyzer.analyze(inputs)
    val dependencyResolverFactory = DependencyResolverFactory(context)
    Validator(grip.classRegistry, errorReporter, context, dependencyResolverFactory).validate()
    checkErrors()
    return context
  }

  private fun copyAndPatchClasses(injectionContext: InjectionContext, generationContext: GenerationContext) {
    fileSourcesAndSinks.forEach { (fileSource, fileSink) ->
      logger.debug("Copy from {} to {}", fileSource, fileSink)
      fileSource.listFiles { path, type ->
        logger.debug("Copy file {} of type {}", path, type)
        when (type) {
          FileSource.EntryType.CLASS -> {
            val classReader = ClassReader(fileSource.readFile(path))
            val classWriter = StandaloneClassWriter(
              classReader, ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES, grip.classRegistry
            )
            val classVisitor = Patcher(classWriter, grip.classRegistry, injectionContext, generationContext)
            classReader.accept(classVisitor, ClassReader.SKIP_FRAMES)
            fileSink.createFile(path, classWriter.toByteArray())
          }

          FileSource.EntryType.FILE -> fileSink.createFile(path, fileSource.readFile(path))
          FileSource.EntryType.DIRECTORY -> fileSink.createDirectory(path)
        }
      }

      fileSink.flush()
    }

    checkErrors()
  }

  private fun performGeneration(injectionContext: InjectionContext, generationContext: GenerationContext) {
    val generator = Generator(grip.classRegistry, errorReporter, classSink)
    generator.generate(injectionContext, generationContext)
    checkErrors()
  }

  private fun checkErrors() {
    if (errorReporter.hasErrors) {
      throw ProcessingException("Errors found")
    }
  }

  private fun InjectionContext.dump() {
    if (!logger.isDebugEnabled) {
      return
    }

    components.forEach { it.dump() }
    contractConfigurations.forEach { it.dump() }
    injectableTargets.forEach { it.dump("Injectable") }
    providableTargets.forEach { it.dump("Providable") }
  }

  private fun Component.dump() {
    logger.debug("Component: {}", type)
    defaultModule.dump("  ")

    for (subcomponent in subcomponents) {
      logger.debug("  Subcomponent: {}", subcomponent)
    }
  }

  private fun ContractConfiguration.dump() {
    logger.debug("Contract Configuration: {}", type)
    contract.dump("  ")
    defaultModule.dump("  ")
  }

  private fun Module.dump(indent: String = "") {
    val nextIntent = "$indent  "
    logger.debug("${indent}Module: {}", type)
    for (provisionPoint in provisionPoints) {
      exhaustive(
        when (provisionPoint) {
          is ProvisionPoint.Constructor ->
            logger.debug("${nextIntent}Constructor: {}", provisionPoint.method)
          is ProvisionPoint.Method ->
            logger.debug("${nextIntent}Method: {}", provisionPoint.method)
          is ProvisionPoint.Field ->
            logger.debug("${nextIntent}Field: {}", provisionPoint.field)
        }
      )
    }

    for (binding in bindings) {
      logger.debug("${nextIntent}Binding: {} -> {}", binding.ancestor, binding.dependency)
    }

    for (factory in factories) {
      logger.debug("${nextIntent}Factory: {}", factory.type)
    }

    for (contract in contracts) {
      contract.dump(nextIntent)
    }

    logger.debug("${nextIntent}Imports:")
    val importIndent = "  $nextIntent"
    for (import in imports) {
      exhaustive(
        when (import) {
          is Import.Module -> import.module.dump(importIndent)
          is Import.Contract -> import.contract.dump(importIndent)
        }
      )
    }
  }

  private fun Contract.dump(indent: String = "") {
    val nextIntent = "$indent  "
    logger.debug("${indent}Contract: {}", type)
    for (provisionPoint in provisionPoints) {
      logger.debug("${nextIntent}Method: {}", provisionPoint.method)
    }
  }

  private fun InjectionTarget.dump(name: String) {
    logger.debug("{}: {}", name, type)
    for (injectionPoint in injectionPoints) {
      when (injectionPoint) {
        is InjectionPoint.Field -> logger.debug("  Field: {}", injectionPoint.field)
        is InjectionPoint.Method -> logger.debug("  Method: {}", injectionPoint.method)
      }
    }
  }
}
