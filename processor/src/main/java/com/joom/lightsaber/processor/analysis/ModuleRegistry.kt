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

import com.joom.lightsaber.ImportedBy
import com.joom.lightsaber.ProvidedBy
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.model.Module
import io.michaelrocks.grip.Grip
import io.michaelrocks.grip.annotatedWith
import io.michaelrocks.grip.classes
import io.michaelrocks.grip.mirrors.ClassMirror
import io.michaelrocks.grip.mirrors.Type
import java.io.File
import java.util.HashMap

interface ModuleRegistry {
  fun getModule(moduleType: Type.Object): Module
}

class ModuleRegistryImpl(
  private val grip: Grip,
  private val moduleParser: ModuleParser,
  private val errorReporter: ErrorReporter,
  providableTargets: Collection<InjectionTarget>,
  factories: Collection<Factory>,
  contracts: Collection<Contract>,
  files: Collection<File>
) : ModuleRegistry {

  private val externals by lazy(LazyThreadSafetyMode.NONE) {
    val modulesQuery = grip select classes from files where annotatedWith(Types.MODULE_TYPE)
    val modules = modulesQuery.execute().classes

    val defaultModuleTypes = modules.mapNotNull { mirror ->
      val annotation = checkNotNull(mirror.annotations[Types.MODULE_TYPE])
      if (annotation.values[com.joom.lightsaber.Module::isDefault.name] == true) mirror.type else null
    }

    Externals(
      importeeModulesByImporterModules = groupImporteeModulesByImporterModules(modules),
      providableTargetsByModules = groupEntitiesByModules(providableTargets, defaultModuleTypes) { it.type },
      factoriesByModules = groupEntitiesByModules(factories, defaultModuleTypes) { it.type },
      contractsByModules = groupEntitiesByModules(contracts, defaultModuleTypes) { it.type }
    )
  }

  private val modulesByTypes = HashMap<Type.Object, Module>()

  private val moduleTypeStack = ArrayList<Type.Object>()

  override fun getModule(moduleType: Type.Object): Module {
    return withModuleTypeInStack(moduleType) {
      maybeParseModule(moduleType)
    }
  }

  private fun groupImporteeModulesByImporterModules(modules: Collection<ClassMirror>): Map<Type.Object, Collection<Type.Object>> {
    return HashMap<Type.Object, MutableList<Type.Object>>().also { importeeModulesByImporterModules ->
      modules.forEach { importee ->
        extractImporterModulesFromModule(importee).forEach { importerType ->
          importeeModulesByImporterModules.getOrPut(importerType, ::ArrayList).add(importee.type)
        }
      }
    }
  }

  private fun extractImporterModulesFromModule(importee: ClassMirror): List<Type.Object> {
    val annotation = importee.annotations[Types.IMPORTED_BY_TYPE] ?: return emptyList()
    val importerTypes = annotation.values[ImportedBy::value.name] as List<*>

    if (importerTypes.isEmpty()) {
      errorReporter.reportError("Module ${importee.type.className} should be imported by at least one module")
      return emptyList()
    } else {
      return importerTypes.mapNotNull {
        val importerType = it as? Type.Object
        if (importerType == null) {
          errorReporter.reportError("A non-class type is specified in @ProvidedBy annotation for ${importee.type.className}")
          return@mapNotNull null
        }

        val importer = grip.classRegistry.getClassMirror(importerType)
        if (Types.MODULE_TYPE !in importer.annotations && Types.COMPONENT_TYPE !in importer.annotations) {
          errorReporter.reportError("Module ${importee.type.className} is imported by ${importerType.className}, which isn't a module")
          return@mapNotNull null
        }

        importerType
      }
    }
  }

  private fun <T : Any> groupEntitiesByModules(
    entities: Collection<T>,
    defaultModuleTypes: Collection<Type.Object>,
    typeSelector: (T) -> Type.Object
  ): Map<Type.Object, List<T>> {
    return HashMap<Type.Object, MutableList<T>>().also { entitiesByModule ->
      entities.forEach { entity ->
        val type = typeSelector(entity)
        val mirror = grip.classRegistry.getClassMirror(type)
        val providedByAnnotation = mirror.annotations[Types.PROVIDED_BY_TYPE]
        val moduleTypes = if (providedByAnnotation != null) providedByAnnotation.values[ProvidedBy::value.name] as List<*> else defaultModuleTypes

        if (moduleTypes.isEmpty()) {
          errorReporter.reportError(
            "Class ${type.className} should be bound to at least one module. " +
                "You can annotate it with @ProvidedBy with a module list " +
                "or make some of your modules default with @Module(isDefault = true)"
          )
        } else {
          moduleTypes.forEach { moduleType ->
            if (moduleType is Type.Object) {
              entitiesByModule.getOrPut(moduleType, ::ArrayList).add(entity)
            } else {
              errorReporter.reportError("A non-class type is specified in @ProvidedBy annotation for ${mirror.type.className}")
            }
          }
        }
      }
    }
  }

  private fun maybeParseModule(moduleType: Type.Object): Module {
    return modulesByTypes.getOrPut(moduleType) {
      val externals = externals
      val importeeModuleTypes = externals.importeeModulesByImporterModules[moduleType].orEmpty()
      val providableTargetsForModuleType = externals.providableTargetsByModules[moduleType].orEmpty()
      val factoriesForModuleType = externals.factoriesByModules[moduleType].orEmpty()
      val importedContractsForModuleType = externals.contractsByModules[moduleType].orEmpty()

      moduleParser.parseModule(
        moduleType,
        importeeModuleTypes,
        providableTargetsForModuleType,
        factoriesForModuleType,
        importedContractsForModuleType,
        this
      )
    }
  }

  private inline fun <T : Any> withModuleTypeInStack(moduleType: Type.Object, action: () -> T): T {
    moduleTypeStack += moduleType
    return try {
      if (moduleTypeStack.indexOf(moduleType) == moduleTypeStack.lastIndex) {
        action()
      } else {
        val cycle = moduleTypeStack.joinToString(" -> ") { it.className }
        throw ModuleParserException("Module cycle: $cycle")
      }
    } finally {
      val removedModuleType = moduleTypeStack.removeAt(moduleTypeStack.lastIndex)
      check(removedModuleType === moduleType)
    }
  }

  private class Externals(
    val importeeModulesByImporterModules: Map<Type.Object, Collection<Type.Object>>,
    val providableTargetsByModules: Map<Type.Object, Collection<InjectionTarget>>,
    val factoriesByModules: Map<Type.Object, Collection<Factory>>,
    val contractsByModules: Map<Type.Object, Collection<Contract>>
  )
}
