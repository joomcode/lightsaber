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

package com.joom.lightsaber.processor

import com.joom.grip.Grip
import com.joom.grip.GripFactory
import com.joom.grip.io.DirectoryFileSink
import com.joom.grip.io.FileSource
import com.joom.grip.io.IoFactory
import com.joom.lightsaber.processor.analysis.Analyzer
import com.joom.lightsaber.processor.analysis.SourceResolverImpl
import com.joom.lightsaber.processor.commons.StandaloneClassWriter
import com.joom.lightsaber.processor.commons.closeQuietly
import com.joom.lightsaber.processor.commons.exhaustive
import com.joom.lightsaber.processor.generation.GenerationContextFactory
import com.joom.lightsaber.processor.generation.Generator
import com.joom.lightsaber.processor.generation.model.GenerationContext
import com.joom.lightsaber.processor.generation.model.ProviderFactoryImpl
import com.joom.lightsaber.processor.injection.Patcher
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
import com.joom.lightsaber.processor.validation.HintsBuilder
import com.joom.lightsaber.processor.validation.UsageValidator
import com.joom.lightsaber.processor.validation.Validator
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.Closeable
import java.nio.file.Path

class ClassProcessor(
  private val parameters: LightsaberParameters
) : Closeable {

  private val logger = getLogger()

  private val grip: Grip = GripFactory.INSTANCE.create(parameters.inputs + parameters.classpath + parameters.modulesClasspath + parameters.bootClasspath)
  private val errorReporter = parameters.errorReporter
  private val sourceResolver = SourceResolverImpl(grip.fileRegistry, parameters.inputs)

  private val fileSourcesAndSinks = parameters.inputs.zip(parameters.outputs) { input, output ->
    val source = IoFactory.createFileSource(input)
    val sink = IoFactory.createFileSink(input, output)
    source to sink
  }
  private val classSink = DirectoryFileSink(parameters.gen)

  fun processClasses() {
    warmUpGripCaches(grip, parameters.inputs + parameters.modulesClasspath)
    val injectionContext = performAnalysisAndValidation()
    val providerFactory = ProviderFactoryImpl(grip.fileRegistry, parameters.projectName)
    val generationContextFactory = GenerationContextFactory(sourceResolver, grip.fileRegistry, grip.classRegistry, providerFactory, parameters.projectName)
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

    grip.closeQuietly()
  }

  private fun performAnalysisAndValidation(): InjectionContext {
    val context = createAnalyzer().analyze(parameters.inputs, parameters.inputs + parameters.modulesClasspath)
    val dependencyResolverFactory = DependencyResolverFactory(context)
    val hintsBuilder = HintsBuilder(grip.classRegistry)
    Validator(grip.classRegistry, errorReporter, context, dependencyResolverFactory, hintsBuilder).validate()
    UsageValidator(grip, errorReporter, sourceResolver).validateUsage(context, parameters.modulesClasspath)
    checkErrors()
    return context
  }

  private fun createAnalyzer(): Analyzer {
    return Analyzer(grip, errorReporter, parameters.projectName)
  }

  private fun copyAndPatchClasses(injectionContext: InjectionContext, generationContext: GenerationContext) {
    fileSourcesAndSinks.parallelStream().forEach { (fileSource, fileSink) ->
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
    val generator = Generator(grip.classRegistry, errorReporter, classSink, parameters.projectName)
    generator.generate(injectionContext, generationContext)
    checkErrors()
  }

  private fun warmUpGripCaches(grip: Grip, inputs: List<Path>) {
    inputs.flatMap { grip.fileRegistry.findTypesForPath(it) }
      .parallelStream()
      .forEach {
        grip.classRegistry.getClassMirror(it)
      }
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
