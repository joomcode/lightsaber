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
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.ImportPoint
import com.joom.lightsaber.processor.model.Module
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

interface ImportParser {
  fun parseImports(
    mirror: ClassMirror,
    moduleRegistry: ModuleRegistry,
    importeeModuleTypes: Collection<Type.Object>,
    isComponentDefaultModule: Boolean
  ): Collection<Import>
}

class ImportParserImpl(
  private val grip: Grip,
  private val errorReporter: ErrorReporter
) : ImportParser {

  private val logger = getLogger()

  override fun parseImports(
    mirror: ClassMirror,
    moduleRegistry: ModuleRegistry,
    importeeModuleTypes: Collection<Type.Object>,
    isComponentDefaultModule: Boolean
  ): Collection<Import> {
    val isImportable = annotatedWith(Types.IMPORT_TYPE)
    val methodsQuery = grip select methods from mirror where (isImportable and methodType(not(returns(Type.Primitive.Void))))
    val fieldsQuery = grip select fields from mirror where isImportable

    val kind = if (isComponentDefaultModule) "Component" else "Module"
    logger.debug("{}: {}", kind, mirror.type.className)
    val methods = methodsQuery.execute()[mirror.type].orEmpty().mapNotNull { method ->
      logger.debug("  Method: {}", method)
      tryParseImports(method, moduleRegistry)
    }

    val fields = fieldsQuery.execute()[mirror.type].orEmpty().mapNotNull { field ->
      logger.debug("  Field: {}", field)
      tryParseImports(field, moduleRegistry)
    }

    val inverse = importeeModuleTypes.map { importeeType ->
      logger.debug("  Inverse import: {}", importeeType.className)
      val module = moduleRegistry.getModule(importeeType)
      Import.Module(module, ImportPoint.Inverse(mirror.type, importeeType))
    }

    return methods + fields + inverse
  }

  private fun tryParseImports(method: MethodMirror, moduleRegistry: ModuleRegistry): Import.Module? {
    val module = tryParseModule(method.signature.returnType, moduleRegistry) ?: return null
    return Import.Module(module, ImportPoint.Method(method))
  }

  private fun tryParseImports(field: FieldMirror, moduleRegistry: ModuleRegistry): Import.Module? {
    val module = tryParseModule(field.signature.type, moduleRegistry) ?: return null
    return Import.Module(module, ImportPoint.Field(field))
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

