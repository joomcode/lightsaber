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

import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.model.Contract
import io.michaelrocks.grip.Grip
import io.michaelrocks.grip.annotatedWith
import io.michaelrocks.grip.classes
import java.io.File

interface ContractAnalyzer {
  fun analyze(files: Collection<File>): Collection<Contract>
}

class ContractAnalyzerImpl(
  private val grip: Grip,
  private val contractParser: ContractParser
) : ContractAnalyzer {

  override fun analyze(files: Collection<File>): Collection<Contract> {
    val contractsQuery = grip select classes from files where annotatedWith(Types.CONTRACT_TYPE)
    return contractsQuery.execute().classes.map {
      contractParser.parseContract(it.type)
    }
  }
}
