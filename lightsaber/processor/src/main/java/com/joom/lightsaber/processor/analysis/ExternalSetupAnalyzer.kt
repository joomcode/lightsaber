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
import com.joom.grip.annotatedWith
import com.joom.grip.classes
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.Type
import com.joom.lightsaber.ImportedBy
import com.joom.lightsaber.ProvidedBy
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.getDescription
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ExternalSetup
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.ImportPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import java.nio.file.Path

interface ExternalSetupAnalyzer {
  fun analyze(paths: Collection<Path>): ExternalSetup
}

class ExternalSetupAnalyzerImpl(
  private val grip: Grip,
  private val analyzerHelper: AnalyzerHelper,
  private val sourceResolver: SourceResolver,
  private val providableTargets: Collection<InjectionTarget>,
  private val factories: Collection<Factory>,
  private val contracts: Collection<Contract>,
  private val errorReporter: ErrorReporter
) : ExternalSetupAnalyzer {

  override fun analyze(paths: Collection<Path>): ExternalSetup {
    val modulesQuery = grip select classes from paths where annotatedWith(Types.MODULE_TYPE)
    val modules = modulesQuery.execute().classes

    return ExternalSetup(
      annotationModuleImportPointsByImporterModules = groupAnnotationImportPointsByModules(modules),
      providableTargetsByModules = groupEntitiesByModules(providableTargets) { it.type },
      factoriesByModules = groupEntitiesByModules(factories) { it.type },
      contractsByModules = groupEntitiesByModules(contracts) { it.type }
    )
  }

  private fun groupAnnotationImportPointsByModules(modules: Collection<ClassMirror>): Map<Type.Object, Collection<ImportPoint.Annotation>> {
    return HashMap<Type.Object, MutableList<ImportPoint.Annotation>>().also { importeeModulesByImporterModules ->
      modules.forEach { module ->
        extractImportedByAnnotationImportPointsFromModule(module).forEach { importPoint ->
          importeeModulesByImporterModules.getOrPut(importPoint.importerType, ::ArrayList).add(importPoint)
        }
      }
    }
  }

  private fun extractImportedByAnnotationImportPointsFromModule(module: ClassMirror): List<ImportPoint.Annotation> {
    val annotation = module.annotations[Types.IMPORTED_BY_TYPE] ?: return emptyList()
    val importerTypes = annotation.values[ImportedBy::value.name] as List<*>

    if (importerTypes.isEmpty()) {
      errorReporter.reportError("Module ${module.type.className} should be imported by at least one container")
      return emptyList()
    } else {
      return importerTypes.mapNotNull {
        val importerType = it as? Type.Object
        if (importerType == null) {
          errorReporter.reportError("A non-class type is specified in @ImportedBy annotation for ${module.type.className}")
          return@mapNotNull null
        }

        if (!checkTypeCanBeModule(importerType)) {
          errorReporter.reportError("Module ${module.type.className} is imported by ${importerType.className}, which isn't a container")
          return@mapNotNull null
        }

        if (isEntityImportedByForeignModule(module.type, importerType)) {
          errorReporter.reportError("Module ${module.type.className} is imported by ${importerType.className}, which doesn't belong to current inputs")
          return@mapNotNull null
        }

        ImportPoint.Annotation(annotation, importerType, module.type)
      }
    }
  }

  private fun checkTypeCanBeModule(type: Type.Object): Boolean {
    val mirror = grip.classRegistry.getClassMirror(type)
    return analyzerHelper.isModule(mirror)
  }

  private fun <T : Any> groupEntitiesByModules(
    entities: Collection<T>,
    typeSelector: (T) -> Type.Object
  ): Map<Type.Object, Collection<T>> {
    return HashMap<Type.Object, MutableList<T>>().also { entitiesByModule ->
      entities.forEach { entity ->
        val type = typeSelector(entity)
        val mirror = grip.classRegistry.getClassMirror(type)
        val annotation = mirror.annotations[Types.PROVIDED_BY_TYPE] ?: return@forEach
        val moduleTypes = annotation.values[ProvidedBy::value.name] as List<*>

        if (moduleTypes.isEmpty()) {
          errorReporter.reportError("@ProvidedBy should contain at least one container: ${mirror.getDescription()}")
        } else {
          moduleTypes.forEach { moduleType ->
            when {
              moduleType !is Type.Object -> {
                errorReporter.reportError("A non-class type is specified in @ProvidedBy annotation for ${mirror.type.className}")
              }

              !checkTypeCanBeModule(moduleType) -> {
                errorReporter.reportError("${mirror.type.className} is provided by ${moduleType.className}, which isn't a container")
              }

              isEntityImportedByForeignModule(mirror.type, moduleType) -> {
                errorReporter.reportError("${mirror.type.className} is provided by ${moduleType.className}, which doesn't belong to current inputs")
              }

              else -> {
                entitiesByModule.getOrPut(moduleType, ::ArrayList).add(entity)
              }
            }
          }
        }
      }
    }
  }

  private fun isEntityImportedByForeignModule(entity: Type.Object, module: Type.Object): Boolean {
    return sourceResolver.belongsToCurrentInput(entity) && !sourceResolver.belongsToCurrentInput(module)
  }
}
