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
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.model.InjectionContext
import java.nio.file.Path

class Analyzer(
  private val grip: Grip,
  private val errorReporter: ErrorReporter,
  private val projectName: String
) {

  fun analyze(paths: Collection<Path>): InjectionContext {
    val sourceResolver = SourceResolverImpl(grip.fileRegistry, paths)
    val analyzerHelper = AnalyzerHelperImpl(grip.classRegistry, ScopeRegistry(), errorReporter)
    val injectionTargetsAnalyzer = InjectionTargetsAnalyzerImpl(grip, analyzerHelper, errorReporter)
    val (injectableTargets, providableTargets) = injectionTargetsAnalyzer.analyze(paths)
    val bindingsAnalyzer = BindingsAnalyzerImpl(grip, analyzerHelper, errorReporter)
    val bindingsRegistry = bindingsAnalyzer.analyze(paths)
    val factoryParser = FactoryParserImpl(grip, analyzerHelper, errorReporter)
    val factoriesAnalyzer = FactoriesAnalyzerImpl(grip, factoryParser)
    val factories = factoriesAnalyzer.analyze(paths)
    val contractParser = ContractParserImpl(grip, analyzerHelper, errorReporter, projectName)
    val contractsAnalyzer = ContractAnalyzerImpl(grip, contractParser)
    val importParser = ImportParserImpl(grip, contractParser, errorReporter)
    val externalSetupAnalyzer = ExternalSetupAnalyzerImpl(
      grip, analyzerHelper, sourceResolver, injectionTargetsAnalyzer, factoriesAnalyzer, contractsAnalyzer, errorReporter
    )
    externalSetupAnalyzer.analyze(paths)
    val bridgeRegistry = BridgeRegistryImpl(grip.classRegistry)
    val provisionPointFactory = ProvisionPointFactoryImpl(grip, analyzerHelper, bridgeRegistry)
    val moduleParser =
      ModuleParserImpl(grip, analyzerHelper, provisionPointFactory, importParser, contractParser, bindingsAnalyzer, externalSetupAnalyzer, errorReporter)
    val modules = ModuleAnalyzerImpl(grip, moduleParser).analyze(paths)
    val components = ComponentsAnalyzerImpl(grip, moduleParser, errorReporter).analyze(paths)
    val contractConfigurations = ContractConfigurationAnalyzerImpl(grip, analyzerHelper, moduleParser, contractParser).analyze(paths)
    return InjectionContext(modules, components, contractConfigurations, injectableTargets, providableTargets, factories, bindingsRegistry.bindings)
  }
}
