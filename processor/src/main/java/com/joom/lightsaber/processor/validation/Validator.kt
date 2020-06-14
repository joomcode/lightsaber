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

import com.joom.lightsaber.LightsaberTypes
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.graph.findCycles
import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.Converter
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import io.michaelrocks.grip.ClassRegistry
import io.michaelrocks.grip.mirrors.Element
import io.michaelrocks.grip.mirrors.Type

class Validator(
  private val classRegistry: ClassRegistry,
  private val errorReporter: ErrorReporter,
  private val context: InjectionContext,
  private val dependencyResolverFactory: DependencyResolverFactory
) {

  fun validate() {
    performSanityChecks()
    validateComponents()
  }

  private fun performSanityChecks() {
    SanityChecker(classRegistry, errorReporter).performSanityChecks(context)
  }

  private fun validateComponents() {
    val componentGraph = buildComponentGraph(context.components)
    val cycles = componentGraph.findCycles()
    for (cycle in cycles) {
      errorReporter.reportError("Component cycle ${cycle.joinToString(" -> ")}")
    }

    context.components
      .forEach { component ->
        validateNoModuleDuplicates(component, emptyMap())
        validateNoDependencyDuplicates(component, emptyMap())
        validateDependenciesAreResolved(component)
        validateNoDependencyCycles(component)
        validateImportedContracts(component)
      }

    validateInjectionTargetsAreResolved(context.injectableTargets, context.components)
  }

  private fun validateNoModuleDuplicates(
    component: Component,
    moduleToComponentsMap: Map<Type.Object, List<Type.Object>>
  ) {
    val newModuleTypeToComponentMap = HashMap(moduleToComponentsMap)
    component.getModulesWithDescendants().forEach { module ->
      val oldComponents = newModuleTypeToComponentMap[module.type]
      val newComponents = if (oldComponents == null) listOf(component.type) else oldComponents + component.type
      newModuleTypeToComponentMap[module.type] = newComponents
    }

    if (component.subcomponents.isEmpty()) {
      // FIXME: This code will report duplicate errors in some cases.
      newModuleTypeToComponentMap.forEach { (moduleType, componentTypes) ->
        if (componentTypes.size > 1) {
          val moduleName = moduleType.className
          val componentNames = componentTypes.joinToString { it.className }
          errorReporter.reportError(
            "Module $moduleName provided multiple times in a single component hierarchy: $componentNames"
          )
        }
      }
    } else {
      component.subcomponents.forEach { subcomponentType ->
        val subcomponent = context.findComponentByType(subcomponentType)
        if (subcomponent != null) {
          validateNoModuleDuplicates(subcomponent, newModuleTypeToComponentMap)
        } else {
          val subcomponentName = subcomponentType.className
          val componentName = component.type.className
          errorReporter.reportError("Subcomponent $subcomponentName of component $componentName not found")
        }
      }
    }
  }

  private fun validateNoDependencyDuplicates(
    component: Component,
    dependencyTypeToModuleMap: Map<Dependency, List<Type.Object>>
  ) {
    val newDependencyTypeToModuleMap = HashMap(dependencyTypeToModuleMap)
    component.getModulesWithDescendants().forEach { module ->
      module.provisionPoints.forEach { provisionPoint ->
        val oldModules = newDependencyTypeToModuleMap[provisionPoint.dependency]
        val newModules = if (oldModules == null) listOf(module.type) else oldModules + listOf(module.type)
        newDependencyTypeToModuleMap[provisionPoint.dependency] = newModules
      }
    }

    if (component.subcomponents.isEmpty()) {
      // FIXME: This code will report duplicate errors in some cases.
      newDependencyTypeToModuleMap.forEach { (dependency, moduleTypes) ->
        if (moduleTypes.size > 1) {
          val moduleNames = moduleTypes.joinToString { it.className }
          errorReporter.reportError(
            "Dependency $dependency provided multiple times in a single component hierarchy by modules: $moduleNames"
          )
        }
      }
    } else {
      component.subcomponents.forEach { subcomponentType ->
        val subcomponent = context.findComponentByType(subcomponentType)
        if (subcomponent != null) {
          validateNoDependencyDuplicates(subcomponent, newDependencyTypeToModuleMap)
        } else {
          val subcomponentName = subcomponentType.className
          val componentName = component.type.className
          errorReporter.reportError("Subcomponent $subcomponentName of component $componentName not found")
        }
      }
    }
  }

  private fun validateDependenciesAreResolved(component: Component) {
    val resolver = dependencyResolverFactory.getOrCreate(component)
    val unresolvedDependencies = resolver.getUnresolvedDependencies()
    if (unresolvedDependencies.isNotEmpty()) {
      val componentName = component.type.className
      for (unresolvedDependency in unresolvedDependencies) {
        errorReporter.reportError("Unresolved dependency $unresolvedDependency in component $componentName")
      }
    }
  }

  private fun validateNoDependencyCycles(component: Component) {
    val resolver = dependencyResolverFactory.getOrCreate(component)
    val dependencyGraph = resolver.getDependencyGraph()
    val cycles = dependencyGraph.findCycles()
    if (cycles.isNotEmpty()) {
      val componentName = component.type.className
      for (cycle in cycles) {
        val cycleString = cycle.joinToString(" -> ")
        errorReporter.reportError("Dependency cycle $cycleString in component $componentName")
      }
    }
  }

  private fun validateInjectionTargetsAreResolved(
    injectionTargets: Iterable<InjectionTarget>,
    components: Iterable<Component>
  ) {
    val dependencyResolver = dependencyResolverFactory.createEmpty()
    components.forEach { dependencyResolver.add(dependencyResolverFactory.getOrCreate(it)) }

    injectionTargets.forEach { injectionTarget ->
      injectionTarget.injectionPoints.forEach { injectionPoint ->
        validateInjectionPointIsResolved(injectionTarget.type, injectionPoint, dependencyResolver)
      }
    }
  }

  private fun validateInjectionPointIsResolved(
    injectionTargetType: Type.Object,
    injectionPoint: InjectionPoint,
    dependencyResolver: DependencyResolver
  ) {
    val dependencies = getDependenciesForInjectionPoint(injectionPoint)
    val element = getElementForInjectionPoint(injectionPoint)

    val unresolvedDependencies = dependencies.filterNot { dependencyResolver.isResolved(it) }
    if (unresolvedDependencies.isNotEmpty()) {
      val injectionTargetName = injectionTargetType.className
      unresolvedDependencies.forEach { dependency ->
        errorReporter.reportError(
          "Unresolved dependency $dependency in $element at $injectionTargetName"
        )
      }
    }
  }

  private fun getDependenciesForInjectionPoint(injectionPoint: InjectionPoint): Collection<Dependency> {
    return when (injectionPoint) {
      is InjectionPoint.Field -> listOf(injectionPoint.injectee.dependency)
      is InjectionPoint.Method -> injectionPoint.injectees.map { it.dependency }
    }
  }

  private fun getElementForInjectionPoint(injectionPoint: InjectionPoint): Element<out Type> {
    return when (injectionPoint) {
      is InjectionPoint.Field -> injectionPoint.field
      is InjectionPoint.Method -> injectionPoint.method
    }
  }

  private fun validateImportedContracts(component: Component) {
    for (import in component.getImportsWithDescendants()) {
      if (import is Import.Contract) {
        val contract = import.contract
        for (provisionPoint in contract.provisionPoints) {
          if (provisionPoint.injectee.converter is Converter.Adapter) {
            if (provisionPoint.injectee.converter.adapterType != LightsaberTypes.LAZY_ADAPTER_TYPE) {
              errorReporter.reportError("Unsupported wrapper type in imported contract: ${contract.type.className}.${provisionPoint.method.name}")
            }
          }
        }
      }
    }
  }
}
