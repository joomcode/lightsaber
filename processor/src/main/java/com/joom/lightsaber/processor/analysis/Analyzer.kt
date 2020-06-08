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
import com.joom.lightsaber.processor.model.InjectionContext
import io.michaelrocks.grip.Grip
import java.io.File

class Analyzer(
  private val grip: Grip,
  private val errorReporter: ErrorReporter,
  private val projectName: String
) {

  fun analyze(files: Collection<File>): InjectionContext {
    val analyzerHelper = AnalyzerHelperImpl(grip.classRegistry, ScopeRegistry(), errorReporter)
    val (injectableTargets, providableTargets) = InjectionTargetsAnalyzerImpl(grip, analyzerHelper, errorReporter).analyze(files)
    val bindingRegistry = BindingsAnalyzerImpl(grip, analyzerHelper, errorReporter).analyze(files)
    val factories = FactoriesAnalyzerImpl(grip, analyzerHelper, errorReporter, projectName).analyze(files)
    val contractParser = ContractParserImpl(grip, analyzerHelper, errorReporter, projectName)
    val contracts = ContractAnalyzerImpl(grip, contractParser).analyze(files)
    val importParser = ImportParserImpl(grip, errorReporter)
    val moduleParser = ModuleParserImpl(grip, importParser, bindingRegistry, analyzerHelper, projectName)
    val moduleRegistry = ModuleRegistryImpl(grip, moduleParser, errorReporter, providableTargets, factories, contracts, files)
    val components = ComponentsAnalyzerImpl(grip, moduleRegistry, errorReporter).analyze(files)
    return InjectionContext(components, injectableTargets, providableTargets, factories, bindingRegistry.bindings, contracts)
  }
}
