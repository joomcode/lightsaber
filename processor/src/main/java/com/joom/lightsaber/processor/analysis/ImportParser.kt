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
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.logging.getLogger
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.ImportPoint
import io.michaelrocks.grip.Grip
import io.michaelrocks.grip.and
import io.michaelrocks.grip.annotatedWith
import io.michaelrocks.grip.fields
import io.michaelrocks.grip.from
import io.michaelrocks.grip.methodType
import io.michaelrocks.grip.methods
import io.michaelrocks.grip.mirrors.Annotated
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
    annotationImportPoints: Collection<ImportPoint.Annotation>
  ): Collection<Import>
}

class ImportParserImpl(
  private val grip: Grip,
  private val contractParser: ContractParser,
  private val errorReporter: ErrorReporter
) : ImportParser {

  private val logger = getLogger()

  override fun parseImports(
    mirror: ClassMirror,
    moduleRegistry: ModuleRegistry,
    annotationImportPoints: Collection<ImportPoint.Annotation>
  ): Collection<Import> {
    val isImportable = annotatedWith(Types.IMPORT_TYPE)
    val methodsQuery = grip select methods from mirror where (isImportable and methodType(not(returns(Type.Primitive.Void))))
    val fieldsQuery = grip select fields from mirror where isImportable

    logger.debug("{}", mirror.type.className)
    val methods = methodsQuery.execute()[mirror.type].orEmpty().mapNotNull { method ->
      logger.debug("  Method: {}", method)
      tryParseMethodImport(mirror, method, moduleRegistry)
    }

    val fields = fieldsQuery.execute()[mirror.type].orEmpty().mapNotNull { field ->
      logger.debug("  Field: {}", field)
      tryParseFieldImport(mirror, field, moduleRegistry)
    }

    val inverse = annotationImportPoints.map { importPoint ->
      logger.debug("  Annotation: {}", importPoint)
      val module = moduleRegistry.getModule(importPoint.importeeType, isImported = true)
      Import.Module(module, importPoint)
    }

    return methods + fields + inverse
  }

  private fun tryParseMethodImport(mirror: ClassMirror, method: MethodMirror, moduleRegistry: ModuleRegistry): Import? {
    if (method.parameters.isNotEmpty()) {
      errorReporter.reportError("Import method cannot have parameters: ${mirror.type.className}.${method.name}")
      return null
    }

    val type = getImportTypeOrNull(method.signature.returnType, "${mirror.type.className}.${method.name}") ?: return null
    return tryParseImport(method, type, ImportPoint.Method(method), moduleRegistry)
  }

  private fun tryParseFieldImport(mirror: ClassMirror, field: FieldMirror, moduleRegistry: ModuleRegistry): Import? {
    val type = getImportTypeOrNull(field.signature.type, "${mirror.type.className}.${field.name}") ?: return null
    return tryParseImport(field, type, ImportPoint.Field(field), moduleRegistry)
  }

  private fun tryParseImport(element: Annotated, type: Type.Object, importPoint: ImportPoint, moduleRegistry: ModuleRegistry): Import {
    return if (Types.CONTRACT_TYPE in element.annotations) {
      val contract = contractParser.parseContract(type)
      Import.Contract(contract, importPoint)
    } else {
      val module = moduleRegistry.getModule(type, isImported = true)
      Import.Module(module, importPoint)
    }
  }

  private fun getImportTypeOrNull(importType: GenericType, source: String): Type.Object? {
    if (importType !is GenericType.Raw) {
      errorReporter.reportError("Import cannot have a generic type: $importType from $source")
      return null
    }

    val type = importType.type
    if (type !is Type.Object) {
      errorReporter.reportError("Import must be a class: $importType from $source")
      return null
    }

    return type
  }
}

