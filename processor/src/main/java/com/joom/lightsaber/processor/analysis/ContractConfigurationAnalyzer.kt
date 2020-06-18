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

package com.joom.lightsaber.processor.analysis

import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractConfiguration
import io.michaelrocks.grip.Grip
import io.michaelrocks.grip.classes
import io.michaelrocks.grip.mirrors.ClassMirror
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.signature.GenericType
import io.michaelrocks.grip.superType
import java.io.File

interface ContractConfigurationAnalyzer {
  fun analyze(files: Collection<File>): Collection<ContractConfiguration>
}

class ContractConfigurationAnalyzerImpl(
  private val grip: Grip,
  private val moduleRegistry: ModuleRegistry,
  private val contractParser: ContractParser,
  private val errorReporter: ErrorReporter
) : ContractConfigurationAnalyzer {

  override fun analyze(files: Collection<File>): Collection<ContractConfiguration> {
    val configurationsQuery = grip select classes from files where superType { _, type -> type == Types.CONTRACT_CONFIGURATION_TYPE }
    return configurationsQuery.execute().classes.mapNotNull { mirror ->
      val contract = extractConfigurationContract(mirror) ?: return@mapNotNull null
      val module = moduleRegistry.getModule(mirror.type, isImported = false)
      ContractConfiguration(mirror.type, contract, module)
    }
  }

  private fun extractConfigurationContract(mirror: ClassMirror): Contract? {
    val superType = mirror.superType
    check(superType == Types.CONTRACT_CONFIGURATION_TYPE)

    val genericSuperType = mirror.signature.superType
    if (genericSuperType !is GenericType.Parameterized) {
      errorReporter.reportError("Invalid base class of ${mirror.type.className}: $genericSuperType")
      return null
    }

    check(genericSuperType.type == Types.CONTRACT_CONFIGURATION_TYPE)
    check(genericSuperType.typeArguments.size == 1)
    val genericContractType = genericSuperType.typeArguments[0]
    if (genericContractType !is GenericType.Raw) {
      errorReporter.reportError("ContractConfiguration ${mirror.type.className} contains a generic type: $genericContractType")
      return null
    }

    val contractType = genericContractType.type
    if (contractType !is Type.Object) {
      errorReporter.reportError("ContractConfiguration ${mirror.type.className} contains a non-class type: $contractType")
      return null
    }

    return contractParser.parseContract(contractType)
  }
}
