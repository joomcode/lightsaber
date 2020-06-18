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
import com.joom.lightsaber.processor.commons.getDescription
import com.joom.lightsaber.processor.commons.getInjectees
import com.joom.lightsaber.processor.graph.findCycles
import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.Converter
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.reportError
import io.michaelrocks.grip.ClassRegistry

class Validator(
  private val classRegistry: ClassRegistry,
  private val errorReporter: ErrorReporter,
  private val context: InjectionContext,
  private val dependencyResolverFactory: DependencyResolverFactory
) {

  private val leafComponents: Collection<Component> by lazy {
    val leafComponentTypes = context.components.mapTo(LinkedHashSet()) { it.type }
    for (component in context.components) {
      if (component.parent != null) {
        leafComponentTypes -= component.parent
      }
    }

    context.components.filter { it.type in leafComponentTypes }
  }

  fun validate() {
    performSanityChecks()
    validateComponents()
    validateInjectionTargets()
  }

  private fun performSanityChecks() {
    SanityChecker(classRegistry, errorReporter).performSanityChecks(context)
  }

  private fun validateComponents() {
    validateNoComponentCycles()

    for (component in context.components) {
      validateNoModuleDuplicates(component)
      validateNoDependencyDuplicates(component)
      validateDependenciesAreResolved(component)
      validateNoDependencyCycles(component)
      validateImportedContracts(component)
    }
  }

  private fun validateNoComponentCycles() {
    val componentGraph = buildComponentGraph(context.components)
    val cycles = componentGraph.findCycles()
    for (cycle in cycles) {
      errorReporter.reportError {
        append("Component cycle:")
        cycle.forEach { type ->
          append("\n  ")
          append(type.getDescription())
        }
      }
    }
  }

  private fun validateNoModuleDuplicates(component: Component) {
    validateNoDuplicates(component, DependencyResolver::getImportsWithPaths) { importType ->
      append("Class ")
      append(importType.getDescription())
      append(" imported multiple times in a single component hierarchy:")
    }
  }

  private fun validateNoDependencyDuplicates(component: Component) {
    validateNoDuplicates(component, DependencyResolver::getProvidedDependencies) { dependency ->
      append("Dependency ")
      append(dependency.getDescription())
      append(" provided multiple times in a single component hierarchy:")
    }
  }

  private inline fun <T : Any> validateNoDuplicates(
    component: Component,
    fetch: DependencyResolver.() -> Map<T, Collection<DependencyResolverPath>>,
    message: StringBuilder.(T) -> Unit
  ) {
    val resolver = dependencyResolverFactory.getOrCreate(component)
    val parentResolver = component.getParentComponent()?.let { dependencyResolverFactory.getOrCreate(it) }
    val parentValues = parentResolver?.fetch()

    for ((key, paths) in resolver.fetch()) {
      if (paths.size > 1) {
        val parentPathCount = parentValues?.get(key)?.size ?: 0
        check(parentPathCount <= paths.size)
        if (parentPathCount != paths.size) {
          errorReporter.reportError {
            message(key)
            paths.forEachIndexed { index, path ->
              append("\n")
              append(index + 1)
              append(".\n")
              append(path.getDescription(indent = "  "))
            }
          }
        }
      }
    }
  }

  private fun validateDependenciesAreResolved(component: Component) {
    val resolver = dependencyResolverFactory.getOrCreate(component)
    val unresolvedDependencies = resolver.getUnresolvedDependencies()
    if (unresolvedDependencies.isNotEmpty()) {
      for ((unresolvedDependency, paths) in unresolvedDependencies) {
        for (path in paths) {
          errorReporter.reportError("Unresolved dependency ${unresolvedDependency.getDescription()}:\n${path.getDescription("  ")}")
        }
      }
    }
  }

  private fun validateNoDependencyCycles(component: Component) {
    val resolver = dependencyResolverFactory.getOrCreate(component)
    val dependencyGraph = resolver.getDependencyGraph()
    val cycles = dependencyGraph.findCycles()
    if (cycles.isNotEmpty()) {
      for (cycle in cycles) {
        errorReporter.reportError {
          append("Dependency cycle in component ${component.type.getDescription()}:")
          cycle.forEach { type ->
            append("\n  ")
            append(type.getDescription())
          }
        }
      }
    }
  }

  private fun validateImportedContracts(component: Component) {
    for (import in component.getImportsWithDescendants()) {
      if (import is Import.Contract) {
        val contract = import.contract
        for (provisionPoint in contract.provisionPoints) {
          if (provisionPoint.injectee.converter is Converter.Adapter) {
            if (provisionPoint.injectee.converter.adapterType != LightsaberTypes.LAZY_ADAPTER_TYPE) {
              errorReporter.reportError {
                append("Unsupported wrapper type in imported contract ")
                append(contract.type.getDescription())
                append(":\n  ")
                append(provisionPoint.method.getDescription())
              }
            }
          }
        }
      }
    }
  }

  private fun Component.getParentComponent(): Component? {
    val parentType = parent ?: return null
    val parentComponent = context.findComponentByType(parentType)
    if (parentComponent == null) {
      errorReporter.reportError("Parent component ${parentType.getDescription()} of component ${type.getDescription()} not found")
      return null
    }

    return parentComponent
  }

  private fun validateInjectionTargets() {
    for (injectableTarget in context.injectableTargets) {
      if (context.findProvidableTargetByType(injectableTarget.type) == null) {
        validateInjectionTargetDependenciesAreProvidedBySingleComponent(injectableTarget)
      }
    }
  }

  private fun validateInjectionTargetDependenciesAreProvidedBySingleComponent(injectionTarget: InjectionTarget) {
    fun getDependencies(): Sequence<Dependency> = sequence {
      for (injectionPoint in injectionTarget.injectionPoints) {
        for (injectee in injectionPoint.getInjectees()) {
          yield(injectee.dependency)
        }
      }
    }

    for (component in leafComponents) {
      val resolver = dependencyResolverFactory.getOrCreate(component)
      if (getDependencies().all { resolver.isResolved(it) }) {
        return
      }
    }

    errorReporter.reportError("Dependencies of ${injectionTarget.type.className} cannot be fully resolved by any component")
  }
}
