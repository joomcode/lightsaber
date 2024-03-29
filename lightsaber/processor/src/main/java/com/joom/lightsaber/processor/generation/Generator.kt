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

package com.joom.lightsaber.processor.generation

import com.joom.grip.ClassRegistry
import com.joom.grip.io.FileSink
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.annotations.proxy.AnnotationCreator
import com.joom.lightsaber.processor.generation.model.GenerationContext
import com.joom.lightsaber.processor.model.InjectionContext

class Generator(
  private val classRegistry: ClassRegistry,
  private val errorReporter: ErrorReporter,
  private val fileSink: FileSink,
  private val projectName: String,
) {

  private val classProducer = ProcessorClassProducer(fileSink, errorReporter)
  private val annotationCreator = AnnotationCreator(classProducer, classRegistry, projectName)

  fun generate(injectionContext: InjectionContext, generationContext: GenerationContext) {
    generateProviders(generationContext)
    generateFactories(injectionContext, generationContext)
    generateContracts(generationContext)
    generatePackageInvaders(generationContext)
    generateKeyRegistry(generationContext)

    fileSink.flush()
  }

  private fun generateProviders(generationContext: GenerationContext) {
    val generator = ProvidersGenerator(classProducer, classRegistry)
    generator.generate(generationContext)
  }

  private fun generateFactories(injectionContext: InjectionContext, generationContext: GenerationContext) {
    val generator = FactoriesGenerator(classProducer, classRegistry)
    generator.generate(injectionContext, generationContext)
  }

  private fun generateContracts(generationContext: GenerationContext) {
    val generator = ContractsGenerator(classProducer, classRegistry)
    generator.generate(generationContext)
  }

  private fun generatePackageInvaders(generationContext: GenerationContext) {
    val generator = PackageInvadersGenerator(classProducer, classRegistry)
    generator.generate(generationContext)
  }

  private fun generateKeyRegistry(generationContext: GenerationContext) {
    val generator = KeyRegistryClassGenerator(classProducer, classRegistry, annotationCreator, generationContext)
    generator.generate()
  }
}
