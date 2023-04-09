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

package com.joom.lightsaber.processor

import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractConfiguration
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import com.joom.lightsaber.processor.validation.DependencyResolver
import com.joom.lightsaber.processor.validation.DependencyResolverFactory
import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.text.MessageFormat

internal object DebugReport {
  fun dump(injectionContext: InjectionContext, dumpContext: DumpContext) {
    val dependencyResolverFactory = DependencyResolverFactory(injectionContext)
    injectionContext.components.forEach { dumpContext.dump(it) }
    injectionContext.contractConfigurations.forEach { dumpContext.dump(it, dependencyResolverFactory) }
    injectionContext.injectableTargets.forEach { dumpContext.dump(it, "Injectable") }
    injectionContext.providableTargets.forEach { dumpContext.dump(it, "Providable") }
  }

  private fun DumpContext.dump(component: Component) {
    dump("Component: {}", component.type)
    dump(component.defaultModule, "  ")

    for (subcomponent in component.subcomponents) {
      dump("  Subcomponent: {}", subcomponent)
    }
  }

  private fun DumpContext.dump(contractConfiguration: ContractConfiguration, dependencyResolverFactory: DependencyResolverFactory) {
    dump("Contract Configuration: {}", contractConfiguration.type)
    dump(contractConfiguration.contract, "  ")
    dump(contractConfiguration.defaultModule, "  ")
    dump(dependencyResolverFactory.getOrCreate(contractConfiguration), "  ")
  }

  private fun DumpContext.dump(dependencyResolver: DependencyResolver, indent: String = "") {
    val graph = dependencyResolver.getDependencyGraph()

    val providedDependencies = dependencyResolver.getProvidedDependencies()
    val resolvedDependencies = dependencyResolver.getResolvedDependencies()
    val unresolvedDependencies = dependencyResolver.getUnresolvedDependencies()

    if (providedDependencies.isNotEmpty()) {
      dump("${indent}Provided dependencies:")
      providedDependencies.keys.forEach { dependency ->
        dump("$indent  $dependency")
      }
    }

    if (resolvedDependencies.isNotEmpty()) {
      dump("${indent}Resolved dependencies:")
      resolvedDependencies.keys.forEach { dependency ->
        dump("$indent  $dependency")
      }
    }

    if (unresolvedDependencies.isNotEmpty()) {
      dump("${indent}Unresolved dependencies:")
      unresolvedDependencies.keys.forEach { dependency ->
        dump("$indent  $dependency")
      }
    }

    dump("${indent}Graph:")
    graph.vertices.forEach { vertex ->
      dump("$indent  {}", vertex)
      graph.getAdjacentVertices(vertex)?.forEach { adjacent ->
        dump("$indent    {}", adjacent)
      }
    }
  }

  private fun DumpContext.dump(module: Module, indent: String = "") {
    val nextIntent = "$indent  "
    dump("${indent}Module: {}", module.type)
    for (provisionPoint in module.provisionPoints) {
      when (provisionPoint) {
        is ProvisionPoint.Constructor ->
          dump("${nextIntent}Constructor: {}", provisionPoint.method)

        is ProvisionPoint.Method ->
          dump("${nextIntent}Method: {}", provisionPoint.method)

        is ProvisionPoint.Field ->
          dump("${nextIntent}Field: {}", provisionPoint.field)
      }
    }

    for (binding in module.bindings) {
      dump("${nextIntent}Binding: {} -> {}", binding.ancestor, binding.dependency)
    }

    for (factory in module.factories) {
      dump("${nextIntent}Factory: {}", factory.type)
      factory.provisionPoints.forEach { factoryProvisionPoint ->
        dump("$nextIntent  Provision point:")
        dump("$nextIntent    Container type: {}", factoryProvisionPoint.containerType)
        dump("$nextIntent    Method: {}", factoryProvisionPoint.method)
        dump("$nextIntent    Injection point container type: {}", factoryProvisionPoint.injectionPoint.containerType)
        dump("$nextIntent    Injection point method: {}", factoryProvisionPoint.injectionPoint.method)
        factoryProvisionPoint.injectionPoint.injectees.forEachIndexed { index, injectee ->
          dump("$nextIntent    Injectee #{}: {}", index, injectee)
        }
      }
    }

    for (contract in module.contracts) {
      dump(contract, nextIntent)
    }

    dump("${nextIntent}Imports:")
    val importIndent = "  $nextIntent"
    for (import in module.imports) {
      when (import) {
        is Import.Module -> dump(import.module, importIndent)
        is Import.Contract -> dump(import.contract, importIndent)
      }
    }
  }

  private fun DumpContext.dump(contract: Contract, indent: String = "") {
    val nextIntent = "$indent  "
    dump("${indent}Contract: {}", contract.type)
    for (provisionPoint in contract.provisionPoints) {
      dump("${nextIntent}Method: {}", provisionPoint.method)
      dump("${nextIntent}Injectee: {}", provisionPoint.injectee)
    }
  }

  private fun DumpContext.dump(target: InjectionTarget, name: String) {
    dump("{}: {}", name, target.type)
    for (injectionPoint in target.injectionPoints) {
      when (injectionPoint) {
        is InjectionPoint.Field -> dump("  Field: {}", injectionPoint.field)
        is InjectionPoint.Method -> dump("  Method: {}", injectionPoint.method)
      }
    }
  }
}

internal interface DumpContext : Closeable {
  fun dump(format: String, vararg args: Any?)
}

internal class FileDumpContext(
  private val file: File
) : DumpContext {
  private val writer = PrintWriter(file)

  override fun dump(format: String, vararg args: Any?) {
    if (args.isNotEmpty()) {
      writer.println(MessageFormat.format(prepareFormatArgs(format), *args))
    } else {
      writer.println(format)
    }
  }

  override fun close() {
    writer.close()
  }

  private fun prepareFormatArgs(format: String): String {
    val parts = format.split("{}")

    if (parts.size == 1) {
      return format
    }

    return buildString {
      parts.forEachIndexed { index, part ->
        if (index != 0) {
          append("{").append(index - 1).append("}")
        }
        append(part)
      }
    }
  }
}
