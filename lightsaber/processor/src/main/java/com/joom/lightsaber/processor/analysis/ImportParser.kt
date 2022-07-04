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
import com.joom.grip.and
import com.joom.grip.annotatedWith
import com.joom.grip.fields
import com.joom.grip.from
import com.joom.grip.methodType
import com.joom.grip.methods
import com.joom.grip.mirrors.Annotated
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.FieldMirror
import com.joom.grip.mirrors.MethodMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.signature.GenericType
import com.joom.grip.not
import com.joom.grip.returns
import com.joom.lightsaber.LightsaberTypes
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.logging.getLogger
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.ImportPoint

interface ImportParser {
  fun parseImports(
    mirror: ClassMirror,
    moduleParser: ModuleParser,
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
    moduleParser: ModuleParser,
    annotationImportPoints: Collection<ImportPoint.Annotation>
  ): Collection<Import> {
    val isImportable = annotatedWith(Types.IMPORT_TYPE)
    val methodsQuery = grip select methods from mirror where (isImportable and methodType(not(returns(Type.Primitive.Void))))
    val fieldsQuery = grip select fields from mirror where isImportable

    logger.debug("{}", mirror.type.className)
    val methods = methodsQuery.execute()[mirror.type].orEmpty().mapNotNull { method ->
      logger.debug("  Method: {}", method)
      tryParseMethodImport(mirror, method, moduleParser)
    }

    val fields = fieldsQuery.execute()[mirror.type].orEmpty().mapNotNull { field ->
      logger.debug("  Field: {}", field)
      tryParseFieldImport(mirror, field, moduleParser)
    }

    val inverse = annotationImportPoints.map { importPoint ->
      logger.debug("  Annotation: {}", importPoint)
      val module = moduleParser.parseModule(importPoint.importeeType, isImported = true)
      Import.Module(module, importPoint)
    }

    return methods + fields + inverse
  }

  private fun tryParseMethodImport(mirror: ClassMirror, method: MethodMirror, moduleParser: ModuleParser): Import? {
    if (method.parameters.isNotEmpty()) {
      errorReporter.reportError("Import method cannot have parameters: ${mirror.type.className}.${method.name}")
      return null
    }

    val type = getImportTypeOrNull(method.signature.returnType, "${mirror.type.className}.${method.name}") ?: return null
    val converter = calculateImportPointConverter(method.signature.returnType)

    return tryParseImport(method, type, ImportPoint.Method(method, converter), moduleParser)
  }

  private fun tryParseFieldImport(mirror: ClassMirror, field: FieldMirror, moduleParser: ModuleParser): Import? {
    val type = getImportTypeOrNull(field.signature.type, "${mirror.type.className}.${field.name}") ?: return null
    val converter = calculateImportPointConverter(field.signature.type)

    return tryParseImport(field, type, ImportPoint.Field(field, converter), moduleParser)
  }

  private fun calculateImportPointConverter(importType: GenericType): ImportPoint.Converter {
    return when {
      isLazyWrapped(importType) -> ImportPoint.Converter.Adapter(LightsaberTypes.LAZY_ADAPTER_TYPE)
      isKotlinLazyWrapped(importType) -> ImportPoint.Converter.Adapter(Types.KOTLIN_LAZY_TYPE)
      else -> ImportPoint.Converter.Instance
    }
  }

  private fun tryParseImport(element: Annotated, type: Type.Object, importPoint: ImportPoint, moduleParser: ModuleParser): Import {
    return if (Types.CONTRACT_TYPE in element.annotations) {
      val contract = contractParser.parseContract(type)
      Import.Contract(contract, importPoint)
    } else {
      val converter = importPoint.converter
      if (converter is ImportPoint.Converter.Adapter) {
        errorReporter.reportError("Imported module with type: ${type.className} cannot be wrapped in: ${converter.adapterType.className}")
      }

      val module = moduleParser.parseModule(type, isImported = true)
      Import.Module(module, importPoint)
    }
  }

  private fun getImportTypeOrNull(importType: GenericType, source: String): Type.Object? {
    val isLazyWrapped = isLazyWrapped(importType) || isKotlinLazyWrapped(importType)

    if (!isLazyWrapped && importType !is GenericType.Raw) {
      errorReporter.reportError("Import cannot have a generic type: $importType from $source")
      return null
    }

    val type = if (isLazyWrapped) {
      ((importType as GenericType.Parameterized).typeArguments.firstOrNull() as? GenericType.Raw)?.type
    } else {
      (importType as GenericType.Raw).type
    }

    if (type !is Type.Object) {
      errorReporter.reportError("Import must be a class: $importType from $source")
      return null
    }

    return type
  }

  private fun isLazyWrapped(importType: GenericType): Boolean {
    return importType is GenericType.Parameterized && importType.type == Types.LAZY_TYPE
  }

  private fun isKotlinLazyWrapped(importType: GenericType): Boolean {
    return importType is GenericType.Parameterized && importType.type == Types.KOTLIN_LAZY_TYPE
  }
}

