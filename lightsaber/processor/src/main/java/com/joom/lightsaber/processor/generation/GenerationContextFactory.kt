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

package com.joom.lightsaber.processor.generation

import com.joom.grip.ClassRegistry
import com.joom.grip.FileRegistry
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.getObjectTypeByInternalName
import com.joom.grip.mirrors.isPublic
import com.joom.grip.mirrors.packageName
import com.joom.grip.mirrors.signature.GenericType
import com.joom.lightsaber.processor.analysis.SourceResolver
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.associateByIndexedNotNullTo
import com.joom.lightsaber.processor.commons.associateByIndexedTo
import com.joom.lightsaber.processor.commons.boxed
import com.joom.lightsaber.processor.commons.getInjectees
import com.joom.lightsaber.processor.descriptors.FieldDescriptor
import com.joom.lightsaber.processor.generation.model.GenerationContext
import com.joom.lightsaber.processor.generation.model.Key
import com.joom.lightsaber.processor.generation.model.KeyRegistry
import com.joom.lightsaber.processor.generation.model.PackageInvader
import com.joom.lightsaber.processor.generation.model.Provider
import com.joom.lightsaber.processor.generation.model.ProviderFactory
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.Module

class GenerationContextFactory(
  private val sourceResolver: SourceResolver,
  private val fileRegistry: FileRegistry,
  private val classRegistry: ClassRegistry,
  private val providerFactory: ProviderFactory,
  private val projectName: String
) {

  fun createGenerationContext(injectionContext: InjectionContext): GenerationContext {
    val modules = injectionContext.getModulesWithDescendants()
    val contractImports = injectionContext.getImportsWithDescendants().filterIsInstance<Import.Contract>()
    val currentInputModules = modules.filter { sourceResolver.belongsToCurrentInput(it.type) }
    val currentInputFactories = injectionContext.factories.filter { sourceResolver.belongsToCurrentInput(it.type) }

    val dependencies = findAllDependencies(modules)
    return GenerationContext(
      providersByModuleType = groupProvidersByModuleType(currentInputModules),
      providersByContractType = groupProvidersByContractType(contractImports),
      packageInvaders = composePackageInvaders(dependencies),
      contracts = groupContracts(currentInputModules),
      factories = currentInputFactories,
      keyRegistry = composeKeyRegistry(dependencies)
    )
  }

  private fun findAllDependencies(modules: Sequence<Module>): Collection<Dependency> {
    return modules.asSequence().flatMapTo(LinkedHashSet()) { getModuleDependencies(it) }
  }

  private fun getModuleDependencies(module: Module): Sequence<Dependency> {
    return sequence {
      module.provisionPoints.forEach { provisionPoint ->
        yield(provisionPoint.dependency)
        provisionPoint.getInjectees().forEach {
          yield(it.dependency)
        }
      }
      module.bindings.forEach { yield(it.ancestor) }
      module.factories.forEach { yield(it.dependency) }
      module.contracts.forEach { yield(it.dependency) }
      module.imports.forEach { import ->
        if (import is Import.Contract) {
          yield(import.contract.dependency)
          import.contract.provisionPoints.forEach { yield(it.injectee.dependency) }
        }
      }
    }
  }

  private fun groupProvidersByModuleType(modules: Sequence<Module>): Map<Type.Object, Collection<Provider>> {
    return modules
      .distinctBy { it.type }
      .associateBy(
        keySelector = { it.type },
        valueTransform = { providerFactory.createProvidersForModule(it) }
      )
  }

  private fun groupProvidersByContractType(contractImports: Sequence<Import.Contract>): Map<Type.Object, Collection<Provider>> {
    return contractImports
      .distinctBy { it.contract.type }
      .associateBy(
        keySelector = { it.contract.type },
        valueTransform = {
          providerFactory.createProvidersForContract(
            it.contract,
            it.importPoint.converter
          )
        }
      )
  }

  private fun groupContracts(modules: Sequence<Module>): Collection<Contract> {
    return modules
      .flatMap { it.contracts.asSequence() }
      .distinctBy { it.type }
      .toList()
  }

  private fun composePackageInvaders(dependencies: Collection<Dependency>): Collection<PackageInvader> {
    return dependencies
      .flatMap { extractObjectTypes(it.type) }
      .distinct()
      .filterNot { isPublicType(it) }
      .groupByTo(
        HashMap(),
        { extractPackageName(it) },
        { it }
      )
      .map {
        val (packageName, types) = it
        val packageInvaderType =
          createUniqueObjectTypeByInternalName("$packageName/Lightsaber\$PackageInvader\$$projectName")
        val fields = types.associateByIndexedTo(
          HashMap(),
          { _, type -> type },
          { index, _ -> FieldDescriptor("class$index", Types.CLASS_TYPE) }
        )
        PackageInvader(packageInvaderType, packageName, fields)
      }
  }

  private fun extractObjectTypes(type: GenericType): List<Type> {
    return when (type) {
      is GenericType.Raw -> extractObjectTypes(type.type)
      is GenericType.TypeVariable -> extractObjectTypes(type.classBound) + type.interfaceBounds.flatMap { extractObjectTypes(it) }
      is GenericType.Array -> extractObjectTypes(type.elementType)
      is GenericType.Parameterized -> listOf(type.type) + type.typeArguments.flatMap { extractObjectTypes(it) }
      is GenericType.Inner -> extractObjectTypes(type.ownerType) + extractObjectTypes(type.type)
      is GenericType.UpperBounded -> extractObjectTypes(type.upperBound)
      is GenericType.LowerBounded -> extractObjectTypes(type.lowerBound)
    }
  }

  private fun extractObjectTypes(type: Type): List<Type> {
    return when (type) {
      is Type.Primitive -> emptyList()
      else -> listOf(type)
    }
  }

  private fun isPublicType(type: Type): Boolean {
    return when (type) {
      is Type.Primitive -> true
      is Type.Array -> isPublicType(type.elementType)
      is Type.Object -> classRegistry.getClassMirror(type).isPublic
      is Type.Method -> error("Method handles aren't supported")
    }
  }

  private fun extractPackageName(type: Type): String {
    return when (type) {
      is Type.Primitive -> error("Cannot get a package for a primitive type $type")
      is Type.Array -> extractPackageName(type.elementType)
      is Type.Object -> type.packageName
      is Type.Method -> error("Method handles aren't supported")
    }
  }

  private fun composeKeyRegistry(dependencies: Collection<Dependency>): KeyRegistry {
    val type = createUniqueObjectTypeByInternalName("com/joom/lightsaber/KeyRegistry\$$projectName")
    val keys = dependencies.associateByIndexedNotNullTo(
      HashMap(),
      { _, dependency -> dependency.boxed() },
      { index, dependency -> maybeComposeKey("key$index", dependency) }
    )
    return KeyRegistry(type, keys)
  }

  private fun maybeComposeKey(name: String, dependency: Dependency): Key? {
    return when {
      dependency.qualifier != null -> Key.QualifiedType(FieldDescriptor(name, Types.KEY_TYPE))
      dependency.type !is GenericType.Raw -> Key.Type(FieldDescriptor(name, Types.TYPE_TYPE))
      else -> null
    }
  }

  private fun createUniqueObjectTypeByInternalName(internalName: String): Type.Object {
    val type = getObjectTypeByInternalName(internalName)
    return if (type !in fileRegistry) type else createUniqueObjectTypeByInternalName(internalName, 0)
  }

  private tailrec fun createUniqueObjectTypeByInternalName(internalName: String, index: Int): Type.Object {
    val type = getObjectTypeByInternalName(internalName + index)
    return if (type !in fileRegistry) type else createUniqueObjectTypeByInternalName(internalName, index + 1)
  }
}
