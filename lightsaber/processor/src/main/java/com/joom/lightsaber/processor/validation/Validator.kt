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

import com.joom.grip.ClassRegistry
import com.joom.lightsaber.LightsaberTypes
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.LightsaberParameters
import com.joom.lightsaber.processor.commons.getDescription
import com.joom.lightsaber.processor.commons.getInjectees
import com.joom.lightsaber.processor.graph.findCycles
import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.ContractConfiguration
import com.joom.lightsaber.processor.model.Converter
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.reportError

class Validator(
  private val classRegistry: ClassRegistry,
  private val errorReporter: ErrorReporter,
  private val context: InjectionContext,
  private val dependencyResolverFactory: DependencyResolverFactory,
  private val hintsBuilder: HintsBuilder,
  private val parameters: LightsaberParameters
) {
  private val unusedImportsCalculator by lazy {
    UnusedImportsCalculator(
      DependencyResolverFactory(
        injectionContext = context,
        includeAllDependenciesInGraph = true
      )
    )
  }

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
    validateContractConfigurations()
    validateImportedContracts()
    validateBindings()
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
    validateNoDuplicateValues(component, DependencyResolver::getImportsWithPaths) { importType ->
      append("Class ")
      append(importType.getDescription())
      append(" imported multiple times in a single component hierarchy:")
    }
  }

  private fun validateNoDependencyDuplicates(component: Component) {
    validateNoDuplicateValues(component, DependencyResolver::getProvidedDependencies) { dependency ->
      append("Dependency ")
      append(dependency.getDescription())
      append(" provided multiple times in a single component hierarchy:")
    }
  }

  private fun validateDependenciesAreResolved(component: Component) {
    validateDependenciesAreResolved(dependencyResolverFactory.getOrCreate(component))
  }

  private fun validateNoDependencyCycles(component: Component) {
    validateNoDependencyCycles(dependencyResolverFactory.getOrCreate(component)) {
      append("Dependency cycle in component ")
      append(component.type.getDescription())
      append(":")
    }
  }

  private fun validateContractConfigurations() {
    for (contractConfiguration in context.contractConfigurations) {
      validateNoModuleDuplicates(contractConfiguration)
      validateNoDependencyDuplicates(contractConfiguration)
      validateDependenciesAreResolved(contractConfiguration)
      validateNoDependencyCycles(contractConfiguration)

      if (parameters.validateUnusedImports) {
        validateNoUnusedImports(contractConfiguration)
      }
    }
  }

  private fun validateNoModuleDuplicates(contractConfiguration: ContractConfiguration) {
    validateNoDuplicateValues(contractConfiguration, DependencyResolver::getImportsWithPaths) { importType ->
      append("Class ")
      append(importType.getDescription())
      append(" imported multiple times in a contract:")
    }
  }

  private fun validateNoDependencyDuplicates(contractConfiguration: ContractConfiguration) {
    validateNoDuplicateValues(contractConfiguration, DependencyResolver::getProvidedDependencies) { dependency ->
      append("Dependency ")
      append(dependency.getDescription())
      append(" provided multiple times in a contract:")
    }
  }

  private fun validateDependenciesAreResolved(contractConfiguration: ContractConfiguration) {
    validateDependenciesAreResolved(dependencyResolverFactory.getOrCreate(contractConfiguration))
  }

  private fun validateNoDependencyCycles(contractConfiguration: ContractConfiguration) {
    validateNoDependencyCycles(dependencyResolverFactory.getOrCreate(contractConfiguration)) {
      append("Dependency cycle in contract ")
      append(contractConfiguration.type.getDescription())
      append(":")
    }
  }

  private fun validateNoUnusedImports(contractConfiguration: ContractConfiguration) {
    val unusedImports = unusedImportsCalculator.findUnusedImports(contractConfiguration)

    if (unusedImports.isEmpty()) {
      return
    }

    errorReporter.reportError {
      append("Found unused imports in a contract configuration ${contractConfiguration.type.getDescription()}:")

      unusedImports.forEach { import ->
        when (import) {
          is Import.Contract -> {
            appendLine()
            append("  - Contract ${import.contract.type.getDescription()}")
          }

          is Import.Module -> {
            appendLine()
            append("  - Module ${import.module.type.getDescription()}")
          }
        }
      }
    }
  }

  private fun validateImportedContracts() {
    for (import in context.getImportsWithDescendants()) {
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

  private fun validateBindings() {
    for (binding in context.bindings) {
      val isResolvedByComponent = context.components.any { component ->
        dependencyResolverFactory.getOrCreate(component).isResolved(binding.dependency)
      }

      val isResolvedByContractConfiguration = context.contractConfigurations.any { contractConfiguration ->
        dependencyResolverFactory.getOrCreate(contractConfiguration).isResolved(binding.dependency)
      }

      val isResolvedByModule = context.modules.any { module ->
        dependencyResolverFactory.getOrCreate(module).isResolved(binding.dependency)
      }

      if (!isResolvedByComponent && !isResolvedByContractConfiguration && !isResolvedByModule) {
        errorReporter.reportError {
          append("Invalid configuration for dependency: ${binding.ancestor.type}. ")
          hintsBuilder.buildHint(binding.dependency)?.let(::append)
        }
      }
    }
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

    val dependencies = getDependencies().toList()
    if (dependencies.isEmpty()) {
      return
    }

    val candidates = findCandidateComponentsForInjectionTargetDependencies(dependencies)
    if (candidates.isNotEmpty()) {
      errorReporter.reportError {
        append("Dependencies of ")
        append(injectionTarget.type.className)
        append(" cannot be fully resolved by any component")
        append("\n")
        append("Best candidates and unresolved dependencies:")
        for (candidate in candidates) {
          append("\n  ")
          append(candidate.component.type.getDescription())

          for (unresolvedDependency in candidate.unresolvedDependencies) {
            append("\n    ")
            append(unresolvedDependency.getDescription())
          }
        }
      }
    }
  }

  private fun findCandidateComponentsForInjectionTargetDependencies(dependencies: Collection<Dependency>): List<CandidateComponent> {
    val candidates = mutableListOf<CandidateComponent>()

    components@ for (component in leafComponents) {
      val resolver = dependencyResolverFactory.getOrCreate(component)
      val unresolvedDependencies = ArrayList<Dependency>(dependencies.size)
      val topCandidateUnresolvedDependencyCount = candidates.firstOrNull()?.unresolvedDependencies?.size ?: Int.MAX_VALUE

      for (dependency in dependencies) {
        if (!resolver.isResolved(dependency)) {
          if (unresolvedDependencies.size >= topCandidateUnresolvedDependencyCount) {
            continue@components
          }

          unresolvedDependencies += dependency
        }
      }

      if (unresolvedDependencies.isEmpty()) {
        return emptyList()
      }

      if (unresolvedDependencies.size < topCandidateUnresolvedDependencyCount) {
        candidates.clear()
      }

      candidates += CandidateComponent(component, unresolvedDependencies)
    }

    return candidates
  }

  private inline fun <T : Any> validateNoDuplicateValues(
    component: Component,
    fetch: DependencyResolver.() -> Map<T, Collection<DependencyResolverPath>>,
    message: StringBuilder.(T) -> Unit
  ) {
    val resolver = dependencyResolverFactory.getOrCreate(component)
    val parentResolver = component.getParentComponent()?.let { dependencyResolverFactory.getOrCreate(it) }
    validateNoDuplicateValues(resolver.fetch(), parentResolver?.fetch(), message)
  }

  private inline fun <T : Any> validateNoDuplicateValues(
    contractConfiguration: ContractConfiguration,
    fetch: DependencyResolver.() -> Map<T, Collection<DependencyResolverPath>>,
    message: StringBuilder.(T) -> Unit
  ) {
    val resolver = dependencyResolverFactory.getOrCreate(contractConfiguration)
    validateNoDuplicateValues(resolver.fetch(), null, message)
  }

  private inline fun <T : Any> validateNoDuplicateValues(
    values: Map<T, Collection<DependencyResolverPath>>,
    parentValues: Map<T, Collection<DependencyResolverPath>>?,
    message: StringBuilder.(T) -> Unit
  ) {
    for ((key, paths) in values) {
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

  private fun validateDependenciesAreResolved(dependencyResolver: DependencyResolver) {
    val unresolvedDependencies = dependencyResolver.getUnresolvedDependencies()
    if (unresolvedDependencies.isNotEmpty()) {
      for ((unresolvedDependency, paths) in unresolvedDependencies) {
        for (path in paths) {
          errorReporter.reportError("Unresolved dependency ${unresolvedDependency.getDescription()}:\n${path.getDescription("  ")}")
        }
      }
    }
  }

  private inline fun validateNoDependencyCycles(dependencyResolver: DependencyResolver, message: StringBuilder.() -> Unit) {
    val dependencyGraph = dependencyResolver.getDependencyGraph()
    val cycles = dependencyGraph.findCycles()
    if (cycles.isNotEmpty()) {
      for (cycle in cycles) {
        errorReporter.reportError {
          message()
          cycle.forEach { type ->
            append("\n  ")
            append(type.getDescription())
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

  private class CandidateComponent(
    val component: Component,
    val unresolvedDependencies: Collection<Dependency>
  )
}
