/*
 * Copyright 2023 SIA Joom
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

import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.signature.GenericType
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.getDescription
import com.joom.lightsaber.processor.graph.DirectedGraph
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractConfiguration
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.Module
import java.util.LinkedList

class UnusedImportsCalculator(
  private val dependencyResolverFactory: DependencyResolverFactory
) {
  fun findUnusedImports(contractConfiguration: ContractConfiguration): List<Import> {
    val dependencyResolver = dependencyResolverFactory.getOrCreate(contractConfiguration)
    val dependencyGraph = dependencyResolver.getDependencyGraph()

    if (dependencyGraph.vertices.any { it.dependsOnInjector(dependencyGraph) }) {
      return emptyList()
    }

    val dependencySources = contractConfiguration.computeDependencySources()
    val usedSources = HashSet<DependencySource>()

    val queuedDependencies = LinkedList<Dependency>()
    val visitedDependencies = HashSet<Dependency>()

    queuedDependencies += contractConfiguration.computeSelfProvidedDependencies()

    while (!queuedDependencies.isEmpty()) {
      val dependency = queuedDependencies.poll()

      usedSources += dependencySources.getOrElse(dependency) {
        error("Unable to find a source for $dependency in ${contractConfiguration.type.getDescription()}")
      }

      dependencyGraph.getAdjacentVertices(dependency)?.forEach { adjacentDependency ->
        if (visitedDependencies.add(adjacentDependency)) {
          queuedDependencies += adjacentDependency
        }
      }
    }

    return contractConfiguration.defaultModule.imports.filter { import ->
      import.toDependencySource() !in usedSources
    }
  }

  private fun ContractConfiguration.computeDependencySources(): Map<Dependency, DependencySource> {
    val dependencySources = HashMap<Dependency, DependencySource>()

    contract.computeProvidedDependencies().forEach { dependency ->
      dependencySources[dependency] = contract.toDependencySource()
    }

    defaultModule.computeProvidedDependencies().forEach { dependency ->
      dependencySources[dependency] = defaultModule.toDependencySource()
    }

    defaultModule.imports.forEach { import ->
      import.computeProvidedDependencies().forEach { dependency ->
        dependencySources[dependency] = import.toDependencySource()
      }
    }

    dependencySources[Dependencies.Injector] = defaultModule.toDependencySource()

    return dependencySources
  }
  
  private fun ContractConfiguration.computeSelfProvidedDependencies(): List<Dependency> {
    val result = LinkedHashSet<Dependency>()
    result += defaultModule.computeProvidedDependencies().toSet()
    result -= defaultModule.computeImportedDependencies().toSet()
    result += contract.computeProvidedDependencies().toSet()
    return result.toList()
  }

  private fun Import.computeProvidedDependencies(): List<Dependency> {
    return when (this) {
      is Import.Contract -> contract.computeProvidedDependencies()
      is Import.Module -> module.computeProvidedDependencies()
    }
  }

  private fun Contract.computeProvidedDependencies(): List<Dependency> {
    return provisionPoints.map { it.injectee.dependency }
  }

  private fun Module.computeProvidedDependencies(): List<Dependency> {
    return dependencyResolverFactory.getOrCreate(this).getProvidedDependencies().keys.toList()
  }

  private fun Module.computeImportedDependencies(): List<Dependency> {
    return imports.flatMap { it.computeProvidedDependencies() }
  }

  private fun Import.toDependencySource(): DependencySource {
    return when (this) {
      is Import.Contract -> contract.toDependencySource()
      is Import.Module -> module.toDependencySource()
    }
  }

  private fun Contract.toDependencySource(): DependencySource {
    return DependencySource(type)
  }

  private fun Module.toDependencySource(): DependencySource {
    return DependencySource(type)
  }

  private fun Dependency.dependsOnInjector(graph: DirectedGraph<Dependency>): Boolean {
    return Dependencies.Injector in graph.getAdjacentVertices(this).orEmpty()
  }

  private data class DependencySource(
    val type: Type.Object
  )

  private object Dependencies {
    val Injector = Dependency(GenericType.Raw(Types.INJECTOR_TYPE))
  }
}
