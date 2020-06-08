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

package com.joom.lightsaber.processor.generation

import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.annotations.proxy.AnnotationCreator
import com.joom.lightsaber.processor.generation.model.GenerationContext
import com.joom.lightsaber.processor.io.FileSink
import com.joom.lightsaber.processor.model.InjectionContext
import io.michaelrocks.grip.ClassRegistry

class Generator(
  private val classRegistry: ClassRegistry,
  private val errorReporter: ErrorReporter,
  private val fileSink: FileSink
) {

  private val classProducer = ProcessorClassProducer(fileSink, errorReporter)
  private val annotationCreator = AnnotationCreator(classProducer, classRegistry)

  fun generate(injectionContext: InjectionContext, generationContext: GenerationContext) {
    generateProviders(injectionContext, generationContext)
    generateFactories(injectionContext, generationContext)
    generateContracts(injectionContext, generationContext)
    generatePackageInvaders(generationContext)
    generateKeyRegistry(generationContext)

    fileSink.flush()
  }

  private fun generateProviders(injectionContext: InjectionContext, generationContext: GenerationContext) {
    val generator = ProvidersGenerator(classProducer, classRegistry)
    generator.generate(injectionContext, generationContext)
  }

  private fun generateFactories(injectionContext: InjectionContext, generationContext: GenerationContext) {
    val generator = FactoriesGenerator(classProducer, classRegistry)
    generator.generate(injectionContext, generationContext)
  }

  private fun generateContracts(injectionContext: InjectionContext, generationContext: GenerationContext) {
    val generator = ContractsGenerator(classProducer, classRegistry)
    generator.generate(injectionContext, generationContext)
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
