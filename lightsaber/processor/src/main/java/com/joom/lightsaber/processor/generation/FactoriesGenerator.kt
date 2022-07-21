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

import com.joom.grip.ClassRegistry
import com.joom.lightsaber.processor.generation.model.GenerationContext
import com.joom.lightsaber.processor.logging.getLogger
import com.joom.lightsaber.processor.model.InjectionContext

class FactoriesGenerator(
  private val classProducer: ClassProducer,
  private val classRegistry: ClassRegistry
) {

  private val logger = getLogger()

  fun generate(injectionContext: InjectionContext, generationContext: GenerationContext) {
    generationContext.factories.forEach { factory ->
      logger.debug("Generating factory {}", factory.implementationType.className)
      val generator = FactoryClassGenerator(classRegistry, generationContext.keyRegistry, injectionContext, factory)
      val factoryClassData = generator.generate()
      classProducer.produceClass(factory.implementationType.internalName, factoryClassData)
    }
  }
}
