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
import com.joom.grip.and
import com.joom.grip.annotatedWith
import com.joom.grip.fields
import com.joom.grip.from
import com.joom.grip.isStatic
import com.joom.grip.methodType
import com.joom.grip.methods
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.not
import com.joom.grip.returns
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.getDescription
import com.joom.lightsaber.processor.logging.getLogger
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import com.joom.lightsaber.processor.reportError

interface ModuleParser {
  fun parseModule(type: Type.Object, isImported: Boolean): Module
}

class ModuleParserImpl(
  private val grip: Grip,
  private val analyzerHelper: AnalyzerHelper,
  private val provisionPointFactory: ProvisionPointFactory,
  private val importParser: ImportParser,
  private val contractParser: ContractParser,
  private val bindingsAnalyzer: BindingsAnalyzer,
  private val externalSetupAnalyzer: ExternalSetupAnalyzer,
  private val errorReporter: ErrorReporter
) : ModuleParser {

  private val logger = getLogger()

  private val modulesByTypes = HashMap<Type.Object, Module>()
  private val moduleTypeStack = ArrayList<Type.Object>()

  override fun parseModule(type: Type.Object, isImported: Boolean): Module {
    return withModuleTypeInStack(type) {
      modulesByTypes.getOrPut(type) {
        val mirror = grip.classRegistry.getClassMirror(type)
        tryParseModule(mirror, isImported) ?: newEmptyModule(type)
      }
    }
  }

  private inline fun withModuleTypeInStack(moduleType: Type.Object, action: () -> Module): Module {
    moduleTypeStack += moduleType
    return try {
      if (moduleTypeStack.indexOf(moduleType) == moduleTypeStack.lastIndex) {
        action()
      } else {
        errorReporter.reportError {
          append("Module cycle:")
          moduleTypeStack.forEach { type ->
            append("\n  ")
            append(type.getDescription())
          }
        }
        newEmptyModule(moduleType)
      }
    } finally {
      val removedModuleType = moduleTypeStack.removeAt(moduleTypeStack.lastIndex)
      check(removedModuleType === moduleType)
    }
  }

  private fun tryParseModule(mirror: ClassMirror, isImported: Boolean): Module? {
    if (isImported) {
      if (Types.MODULE_TYPE !in mirror.annotations) {
        errorReporter.reportError("Imported module ${mirror.getDescription()} isn't annotated with @Module")
        return null
      }
    }

    if (mirror.signature.typeVariables.isNotEmpty()) {
      errorReporter.reportError("Module cannot have a type parameters: ${mirror.type.className}")
      return null
    }

    val path = grip.fileRegistry.findPathForType(mirror.type) ?: run {
      errorReporter.reportError("Failed to find path for module ${mirror.type.className}")
      return null
    }

    val externalSetup = externalSetupAnalyzer.analyze(listOf(path))
    val annotationImportPoints = externalSetup.annotationModuleImportPointsByImporterModules[mirror.type].orEmpty()
    val providableTargets = externalSetup.providableTargetsByModules[mirror.type].orEmpty()
    val factories = externalSetup.factoriesByModules[mirror.type].orEmpty()
    val contracts = externalSetup.contractsByModules[mirror.type].orEmpty()
    val bindingRegistry = bindingsAnalyzer.analyze(listOf(path))

    val imports = importParser.parseImports(mirror, this, annotationImportPoints)
    val provisionPoints = createProvisionPoints(mirror, providableTargets)
    val dependencies = provisionPoints.map { it.dependency } + factories.map { it.dependency } + contracts.map { it.dependency }
    val bindings = dependencies.flatMap { bindingRegistry.findBindingsByDependency(it) }
    val configurationContract = analyzerHelper.findConfigurationContractType(mirror)?.let { contractParser.parseContract(it) }
    return Module(mirror.type, imports, provisionPoints, bindings, factories, contracts + listOfNotNull(configurationContract))
  }

  private fun createProvisionPoints(mirror: ClassMirror, providableTargets: Collection<InjectionTarget>): Collection<ProvisionPoint> {
    val isProvidable = annotatedWith(Types.PROVIDE_TYPE) and not(isStatic())
    val methodsQuery = grip select methods from mirror where (isProvidable and methodType(not(returns(Type.Primitive.Void))))
    val fieldsQuery = grip select fields from mirror where isProvidable

    val provisionPoints = ArrayList<ProvisionPoint>()

    logger.debug("Module: {}", mirror.type.className)
    providableTargets.mapTo(provisionPoints) { target ->
      logger.debug("  Constructor: {}", target.injectionPoints.first())
      provisionPointFactory.newConstructorProvisionPoint(target)
    }

    methodsQuery.execute()[mirror.type].orEmpty().mapTo(provisionPoints) { method ->
      logger.debug("  Method: {}", method)
      provisionPointFactory.newMethodProvisionPoint(mirror.type, method)
    }

    fieldsQuery.execute()[mirror.type].orEmpty().mapTo(provisionPoints) { field ->
      logger.debug("  Field: {}", field)
      provisionPointFactory.newFieldProvisionPoint(mirror.type, field)
    }

    return provisionPoints
  }

  private fun newEmptyModule(type: Type.Object): Module {
    return Module(type, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
  }
}
