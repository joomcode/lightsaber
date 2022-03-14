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

import com.joom.grip.Grip
import com.joom.grip.classes
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.superType
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractConfiguration
import java.io.File
import java.nio.file.Path

interface ContractConfigurationAnalyzer {
  fun analyze(files: Collection<File>): Collection<ContractConfiguration> {
    return analyzePaths(files.map { it.toPath() })
  }

  fun analyzePaths(paths: Collection<Path>): Collection<ContractConfiguration>
}

class ContractConfigurationAnalyzerImpl(
  private val grip: Grip,
  private val analyzerHelper: AnalyzerHelper,
  private val moduleParser: ModuleParser,
  private val contractParser: ContractParser
) : ContractConfigurationAnalyzer {

  override fun analyzePaths(paths: Collection<Path>): Collection<ContractConfiguration> {
    val configurationsQuery = grip select classes from paths where superType { _, type -> type == Types.CONTRACT_CONFIGURATION_TYPE }
    return configurationsQuery.execute().classes.mapNotNull { mirror ->
      val contract = extractConfigurationContract(mirror) ?: return@mapNotNull null
      val module = moduleParser.parseModule(mirror.type, isImported = false)
      ContractConfiguration(mirror.type, contract, module)
    }
  }

  private fun extractConfigurationContract(mirror: ClassMirror): Contract? {
    val contractType = analyzerHelper.findConfigurationContractType(mirror) ?: return null
    return contractParser.parseContract(contractType)
  }
}
