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

package com.joom.lightsaber.processor.validation

import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.commons.AccessFlagStringifier
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.getAncestors
import com.joom.lightsaber.processor.commons.getDescription
import com.joom.lightsaber.processor.commons.rawType
import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.FactoryProvisionPoint
import com.joom.lightsaber.processor.model.ImportPoint
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.ProvisionPoint
import io.michaelrocks.grip.ClassRegistry
import io.michaelrocks.grip.mirrors.ClassMirror
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.isAbstract
import io.michaelrocks.grip.mirrors.isDefaultConstructor
import io.michaelrocks.grip.mirrors.isInterface
import io.michaelrocks.grip.mirrors.isStatic
import io.michaelrocks.grip.mirrors.signature.GenericType
import org.objectweb.asm.Opcodes

class SanityChecker(
  private val classRegistry: ClassRegistry,
  private val errorReporter: ErrorReporter
) {

  fun performSanityChecks(context: InjectionContext) {
    checkStaticInjectionPoints(context)
    checkProvidableTargetsAreConstructable(context)
    checkProviderMethodsReturnValues(context)
    checkSubcomponentsAreComponents(context)
    checkModulesExtendObject(context)
    checkModulesWithImportedByAreDefaultConstructible(context)
    checkFactories(context)
    checkBindingsConnectValidClasses(context)
    checkContractConfigurationsAreConcrete(context)
  }

  private fun checkStaticInjectionPoints(context: InjectionContext) {
    for (injectableTarget in context.injectableTargets) {
      injectableTarget.injectionPoints.forEach { injectionPoint ->
        when (injectionPoint) {
          is InjectionPoint.Field ->
            if (injectionPoint.field.isStatic) {
              errorReporter.reportError("Static field injection is not supported yet: " + injectionPoint.field)
            }
          is InjectionPoint.Method ->
            if (injectionPoint.method.isStatic) {
              errorReporter.reportError("Static method injection is not supported yet: " + injectionPoint.method)
            }
        }
      }
    }
  }

  private fun checkProvidableTargetsAreConstructable(context: InjectionContext) {
    for (providableTarget in context.providableTargets) {
      checkProvidableTargetIsConstructable(providableTarget.type)
    }
  }

  private fun checkProviderMethodsReturnValues(context: InjectionContext) {
    context.getModulesWithDescendants()
      .distinctBy { module -> module.type }
      .flatMap { module -> module.provisionPoints.asSequence() }
      .forEach { provisionPoint ->
        if (provisionPoint !is ProvisionPoint.Constructor && provisionPoint.dependency.type.rawType == Type.Primitive.Void) {
          errorReporter.reportError("Provider returns void: ${composeProvisionPointDescription(provisionPoint)}")
        }
      }
  }

  private fun composeProvisionPointDescription(provisionPoint: ProvisionPoint): String {
    return when (provisionPoint) {
      is ProvisionPoint.Constructor -> "${provisionPoint.containerType.className}()"
      is ProvisionPoint.Method -> "${provisionPoint.containerType.className}.${provisionPoint.injectionPoint.method.name}"
      is ProvisionPoint.Field -> "${provisionPoint.containerType.className}.${provisionPoint.field.name}"
    }
  }

  private fun checkProvidableTargetIsConstructable(providableTarget: Type.Object) {
    val mirror = classRegistry.getClassMirror(providableTarget)
    checkProvidableTargetAccessFlagNotSet(mirror, Opcodes.ACC_INTERFACE)
    checkProvidableTargetAccessFlagNotSet(mirror, Opcodes.ACC_ABSTRACT)
    checkProvidableTargetAccessFlagNotSet(mirror, Opcodes.ACC_ENUM)
    checkProvidableTargetAccessFlagNotSet(mirror, Opcodes.ACC_ANNOTATION)
  }

  private fun checkProvidableTargetAccessFlagNotSet(mirror: ClassMirror, flag: Int) {
    if ((mirror.access and flag) != 0) {
      errorReporter.reportError(
        "Providable class cannot be ${AccessFlagStringifier.classAccessFlagToString(flag)}: ${mirror.type}"
      )
    }
  }

  private fun checkSubcomponentsAreComponents(context: InjectionContext) {
    for (component in context.components) {
      for (subcomponent in component.subcomponents) {
        if (context.findComponentByType(subcomponent) == null) {
          errorReporter.reportError("Subcomponent is not annotated with @Component: ${subcomponent.className}")
        }
      }
    }
  }

  private fun checkModulesExtendObject(context: InjectionContext) {
    // Components are also validated here because every component is a module.
    for (module in context.getModulesWithDescendants()) {
      checkModuleSuperClass(module.type)
    }
  }

  private fun checkModuleSuperClass(type: Type.Object) {
    val mirror = classRegistry.getClassMirror(type)
    if (mirror.superType != Types.OBJECT_TYPE && mirror.superType != Types.CONTRACT_CONFIGURATION_TYPE) {
      errorReporter.reportError(
        "${type.className} has a super type of ${mirror.type.className} instead of " +
            "${Types.OBJECT_TYPE.className} or ${Types.CONTRACT_CONFIGURATION_TYPE.className}"
      )
    }
  }

  private fun checkModulesWithImportedByAreDefaultConstructible(context: InjectionContext) {
    context.getImportsWithDescendants()
      .map { import -> import.importPoint }
      .filterIsInstance<ImportPoint.Inverse>()
      .distinctBy { importPoint -> importPoint.importeeType }
      .forEach { importPoint ->
        val type = importPoint.importeeType
        val mirror = classRegistry.getClassMirror(type)
        if (mirror.constructors.none { it.isDefaultConstructor }) {
          errorReporter.reportError("Module ${type.className} with @ImportedBy annotation must have a default constructor")
        }
      }
  }

  private fun checkFactories(context: InjectionContext) {
    for (factory in context.factories) {
      checkFactory(factory)
    }
  }

  private fun checkFactory(factory: Factory) {
    val mirror = classRegistry.getClassMirror(factory.type)

    if (!mirror.isInterface) {
      errorReporter.reportError("Factory ${mirror.type.className} must be an interface")
      return
    }

    if (mirror.signature.typeVariables.isNotEmpty()) {
      errorReporter.reportError("Factory ${mirror.type.className} mustn't contain generic parameters")
      return
    }

    if (mirror.interfaces.isNotEmpty()) {
      errorReporter.reportError("Factory ${mirror.type.className} mustn't extend any interfaces")
      return
    }

    if (mirror.methods.isEmpty()) {
      errorReporter.reportError("Factory ${mirror.type.className} must contain at least one method")
      return
    }

    for (provisionPoint in factory.provisionPoints) {
      checkFactoryProvisionPoint(provisionPoint)
    }
  }

  private fun checkFactoryProvisionPoint(provisionPoint: FactoryProvisionPoint) {
    val method = provisionPoint.method

    if (method.signature.typeVariables.isNotEmpty()) {
      errorReporter.reportError("Method ${provisionPoint.containerType.className}.${method.name} mustn't contain generic parameters")
      return
    }

    val dependencyType = provisionPoint.injectionPoint.containerType
    val returnType = provisionPoint.method.type.returnType
    if (dependencyType != returnType) {
      if (returnType !in classRegistry.getAncestors(dependencyType)) {
        errorReporter.reportError(
          "Method ${provisionPoint.containerType.className}.${method.name} returns ${returnType.className} which isn't an ancestor of " +
              "${dependencyType.className} from the @Factory.Return annotation"
        )
        return
      }
    }
  }

  private fun checkBindingsConnectValidClasses(context: InjectionContext) {
    val providableTargetTypes = context.providableTargets.mapTo(HashSet()) { it.type }
    for (binding in context.bindings) {
      checkBindingIsValid(binding, providableTargetTypes)
    }
  }

  private fun checkBindingIsValid(binding: Binding, providableTargetTypes: Set<Type.Object>) {
    val dependencyType = if (binding.dependency.type is GenericType.Raw) binding.dependency.type.type as? Type.Object else null
    if (dependencyType == null) {
      errorReporter.reportError("@ProvidedAs host class cannot be a non-raw type ${binding.dependency.type}")
      return
    }

    val ancestorType = if (binding.ancestor.type is GenericType.Raw) binding.ancestor.type.type as? Type.Object else null
    if (ancestorType == null) {
      errorReporter.reportError("@ProvidedAs cannot have a non-class type ${dependencyType.className} as an argument")
      return
    }

    if (dependencyType == ancestorType) {
      errorReporter.reportError("@ProvidedAs bindings cannot have a host class ${dependencyType.className} as an argument")
      return
    }

    val mirror = classRegistry.getClassMirror(dependencyType)
    if (mirror.signature.typeVariables.isNotEmpty()) {
      errorReporter.reportError("@ProvidedAs bindings aren't supported for a generic type ${mirror.type.className}")
      return
    }

    val bindingMirror = classRegistry.getClassMirror(ancestorType)
    if (bindingMirror.signature.typeVariables.isNotEmpty()) {
      errorReporter.reportError("@ProvidedAs bindings cannot have a generic type ${ancestorType.className} as an argument")
      return
    }

    if (ancestorType in providableTargetTypes) {
      errorReporter.reportError("@ProvidedAs binding's argument ${ancestorType.className} cannot have an @Inject constructor")
    }

    if (ancestorType !in classRegistry.getAncestors(mirror.type)) {
      errorReporter.reportError("@ProvidedAs binding's argument ${ancestorType.className} isn't a super type of the host class ${mirror.type.className}")
      return
    }
  }

  private fun checkContractConfigurationsAreConcrete(context: InjectionContext) {
    for (contractConfiguration in context.contractConfigurations) {
      val mirror = classRegistry.getClassMirror(contractConfiguration.type)
      if (mirror.isInterface || mirror.isAbstract) {
        errorReporter.reportError("Contract configuration ${mirror.getDescription()} should be a concrete class")
      }
    }
  }
}
