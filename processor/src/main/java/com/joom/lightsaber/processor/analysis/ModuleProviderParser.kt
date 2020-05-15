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

import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.ProcessingException
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.logging.getLogger
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ModuleProvider
import com.joom.lightsaber.processor.model.ModuleProvisionPoint
import io.michaelrocks.grip.Grip
import io.michaelrocks.grip.and
import io.michaelrocks.grip.annotatedWith
import io.michaelrocks.grip.fields
import io.michaelrocks.grip.from
import io.michaelrocks.grip.methodType
import io.michaelrocks.grip.methods
import io.michaelrocks.grip.mirrors.ClassMirror
import io.michaelrocks.grip.mirrors.FieldMirror
import io.michaelrocks.grip.mirrors.MethodMirror
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.signature.GenericType
import io.michaelrocks.grip.not
import io.michaelrocks.grip.returns

interface ModuleProviderParser {
  fun parseModuleProviders(
    mirror: ClassMirror,
    moduleRegistry: ModuleRegistry,
    importeeModuleTypes: Collection<Type.Object>,
    isComponentDefaultModule: Boolean
  ): Collection<ModuleProvider>
}

class ModuleProviderParserImpl(
  private val grip: Grip,
  private val errorReporter: ErrorReporter
) : ModuleProviderParser {

  private val logger = getLogger()

  override fun parseModuleProviders(
    mirror: ClassMirror,
    moduleRegistry: ModuleRegistry,
    importeeModuleTypes: Collection<Type.Object>,
    isComponentDefaultModule: Boolean
  ): Collection<ModuleProvider> {
    val isImportable = annotatedWith(Types.IMPORT_TYPE)
    val methodsQuery = grip select methods from mirror where (isImportable and methodType(not(returns(Type.Primitive.Void))))
    val fieldsQuery = grip select fields from mirror where isImportable

    val kind = if (isComponentDefaultModule) "Component" else "Module"
    logger.debug("{}: {}", kind, mirror.type.className)
    val methods = methodsQuery.execute()[mirror.type].orEmpty().mapNotNull { method ->
      logger.debug("  Method: {}", method)
      tryParseModuleProvider(method, moduleRegistry)
    }

    val fields = fieldsQuery.execute()[mirror.type].orEmpty().mapNotNull { field ->
      logger.debug("  Field: {}", field)
      tryParseModuleProvider(field, moduleRegistry)
    }

    val inverseImports = importeeModuleTypes.map { importeeType ->
      logger.debug("  Inverse import: {}", importeeType.className)
      val module = moduleRegistry.getModule(importeeType)
      ModuleProvider(module, ModuleProvisionPoint.InverseImport(mirror.type, importeeType))
    }

    return methods + fields + inverseImports
  }

  private fun tryParseModuleProvider(method: MethodMirror, moduleRegistry: ModuleRegistry): ModuleProvider? {
    val module = tryParseModule(method.signature.returnType, moduleRegistry) ?: return null
    return ModuleProvider(module, ModuleProvisionPoint.Method(method))
  }

  private fun tryParseModuleProvider(field: FieldMirror, moduleRegistry: ModuleRegistry): ModuleProvider? {
    val module = tryParseModule(field.signature.type, moduleRegistry) ?: return null
    return ModuleProvider(module, ModuleProvisionPoint.Field(field))
  }

  private fun tryParseModule(generic: GenericType, moduleRegistry: ModuleRegistry): Module? {
    if (generic !is GenericType.Raw) {
      errorReporter.reportError("Module provider cannot have a generic type: $generic")
      return null
    }

    val type = generic.type
    if (type !is Type.Object) {
      errorReporter.reportError("Module provider cannot have an array type: $generic")
      return null
    }

    return try {
      moduleRegistry.getModule(type)
    } catch (exception: ProcessingException) {
      errorReporter.reportError(exception)
      null
    }
  }
}

