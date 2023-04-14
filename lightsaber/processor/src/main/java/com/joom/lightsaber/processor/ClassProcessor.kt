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

import com.joom.grip.CombinedGripFactory
import com.joom.grip.Grip
import com.joom.grip.GripFactory
import com.joom.grip.io.DirectoryFileSink
import com.joom.grip.io.FileSource
import com.joom.grip.io.IoFactory
import com.joom.lightsaber.processor.analysis.Analyzer
import com.joom.lightsaber.processor.analysis.SourceResolverImpl
import com.joom.lightsaber.processor.commons.StandaloneClassWriter
import com.joom.lightsaber.processor.commons.closeQuietly
import com.joom.lightsaber.processor.generation.GenerationContextFactory
import com.joom.lightsaber.processor.generation.Generator
import com.joom.lightsaber.processor.generation.model.GenerationContext
import com.joom.lightsaber.processor.generation.model.ProviderFactoryImpl
import com.joom.lightsaber.processor.injection.Patcher
import com.joom.lightsaber.processor.logging.getLogger
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.validation.DependencyResolverFactory
import com.joom.lightsaber.processor.validation.HintsBuilder
import com.joom.lightsaber.processor.validation.UsageValidator
import com.joom.lightsaber.processor.validation.Validator
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.Closeable
import java.io.File
import java.nio.file.Path

class ClassProcessor(
  private val parameters: LightsaberParameters
) : Closeable {

  private val logger = getLogger()

  private val inputsGrip = GripFactory.INSTANCE.create(parameters.inputs)
  private val grip: Grip = createGrip()
  private val errorReporter = parameters.errorReporter
  private val sourceResolver = SourceResolverImpl(grip.fileRegistry, parameters.inputs)

  private val fileSourcesAndSinks = parameters.inputs.zip(parameters.outputs) { input, output ->
    val source = IoFactory.createFileSource(input)
    val sink = IoFactory.createFileSink(input, output)
    source to sink
  }
  private val classSink = DirectoryFileSink(parameters.gen)

  fun processClasses() {
    warmUpGripCaches(grip, parameters.inputs)

    val injectionContext = performAnalysisAndValidation()
    val providerFactory = ProviderFactoryImpl(grip.fileRegistry, parameters.projectName)

    val generationContextFactory = GenerationContextFactory(
      sourceResolver = sourceResolver,
      fileRegistry = grip.fileRegistry,
      classRegistry = grip.classRegistry,
      providerFactory = providerFactory,
      projectName = parameters.projectName
    )

    val generationContext = generationContextFactory.createGenerationContext(injectionContext)
    copyAndPatchClasses(injectionContext, generationContext)
    performGeneration(injectionContext, generationContext)
  }

  override fun close() {
    classSink.closeQuietly()

    fileSourcesAndSinks.forEach {
      it.first.closeQuietly()
      it.second.closeQuietly()
    }

    inputsGrip.closeQuietly()
  }

  private fun performAnalysisAndValidation(): InjectionContext {
    val context = Analyzer(grip, errorReporter, parameters.projectName).analyze(parameters.inputs)
    val dependencyResolverFactory = DependencyResolverFactory(context)
    val hintsBuilder = HintsBuilder(grip.classRegistry)

    if (parameters.dumpDebugReport) {
      FileDumpContext(getOrCreateReportFile()).use { dumpContext ->
        DebugReport.dump(context, dumpContext)
      }
    }

    Validator(
      classRegistry = grip.classRegistry,
      errorReporter = errorReporter,
      context = context,
      dependencyResolverFactory = dependencyResolverFactory,
      hintsBuilder = hintsBuilder,
      parameters = parameters
    ).validate()

    if (parameters.validateUsage) {
      UsageValidator(grip, errorReporter).validateUsage(parameters.modulesClasspath)
    }

    checkErrors()
    return context
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

  private fun getOrCreateReportFile(): File {
    val report = File(parameters.reportDirectory.toFile(), "debug-dump.txt")
    report.parentFile.mkdirs()
    report.createNewFile()
    if (!report.exists()) {
      error("Unable to create a report file $report")
    }
    return report
  }

  private fun checkErrors() {
    if (errorReporter.hasErrors) {
      throw ProcessingException(errorReporter.errors.joinToString(prefix = "Errors found:\n", separator = "\n"))
    }
  }

  private fun createGrip(): Grip {
    return CombinedGripFactory.INSTANCE.create(
      listOf(inputsGrip) + CachedGripFactory.create(
        parameters.sharedBuildCache,
        parameters.classpath + parameters.modulesClasspath + parameters.bootClasspath
      )
    )
  }
}
