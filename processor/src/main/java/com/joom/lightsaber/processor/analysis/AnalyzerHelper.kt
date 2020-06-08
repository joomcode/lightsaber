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

import com.joom.lightsaber.LightsaberTypes
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.ProcessingException
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.rawType
import com.joom.lightsaber.processor.model.Converter
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Injectee
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.Scope
import io.michaelrocks.grip.ClassRegistry
import io.michaelrocks.grip.mirrors.Annotated
import io.michaelrocks.grip.mirrors.AnnotationMirror
import io.michaelrocks.grip.mirrors.FieldMirror
import io.michaelrocks.grip.mirrors.MethodMirror
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.signature.GenericType

interface AnalyzerHelper {
  fun convertMethodToInjectionPoint(method: MethodMirror, container: Type.Object): InjectionPoint.Method
  fun convertFieldToInjectionPoint(field: FieldMirror, container: Type.Object): InjectionPoint.Field
  fun convertMethodParameterToInjectee(method: MethodMirror, parameterIndex: Int): Injectee
  fun convertMethodResultToInjectee(method: MethodMirror): Injectee
  fun convertFieldToInjectee(field: FieldMirror): Injectee
  fun findQualifier(annotated: Annotated): AnnotationMirror?
  fun findScope(annotated: Annotated): Scope
}

class AnalyzerHelperImpl(
  private val classRegistry: ClassRegistry,
  private val scopeRegistry: ScopeRegistry,
  private val errorReporter: ErrorReporter
) : AnalyzerHelper {

  override fun convertMethodToInjectionPoint(method: MethodMirror, container: Type.Object): InjectionPoint.Method {
    return InjectionPoint.Method(container, method, getInjectees(method))
  }

  override fun convertFieldToInjectionPoint(field: FieldMirror, container: Type.Object): InjectionPoint.Field {
    return InjectionPoint.Field(container, field, getInjectee(field))
  }

  override fun convertMethodParameterToInjectee(method: MethodMirror, parameterIndex: Int): Injectee {
    val type = method.signature.parameterTypes[parameterIndex]
    return newInjectee(type, method.parameters[parameterIndex])
  }

  override fun convertMethodResultToInjectee(method: MethodMirror): Injectee {
    return newInjectee(method.signature.returnType, method)
  }

  override fun convertFieldToInjectee(field: FieldMirror): Injectee {
    return newInjectee(field.signature.type, field)
  }

  override fun findQualifier(annotated: Annotated): AnnotationMirror? {
    fun isQualifier(annotationType: Type.Object): Boolean {
      return classRegistry.getClassMirror(annotationType).annotations.contains(Types.QUALIFIER_TYPE)
    }

    val qualifierCount = annotated.annotations.count { isQualifier(it.type) }
    if (qualifierCount > 0) {
      if (qualifierCount > 1) {
        errorReporter.reportError("Element $this has multiple qualifiers")
      }
      return annotated.annotations.first { isQualifier(it.type) }
    } else {
      return null
    }
  }

  override fun findScope(annotated: Annotated): Scope {
    val scopeProviders = annotated.annotations.mapNotNull {
      scopeRegistry.findScopeProviderByAnnotationType(it.type)
    }

    return when (scopeProviders.size) {
      0 -> Scope.None
      1 -> Scope.Class(scopeProviders[0])

      else -> {
        errorReporter.reportError("Element $this has multiple scopes: $scopeProviders")
        Scope.None
      }
    }
  }

  private fun getInjectees(method: MethodMirror): List<Injectee> {
    return method.parameters.mapIndexed { index, parameter ->
      val type = method.signature.parameterTypes[index]
      newInjectee(type, parameter)
    }
  }

  private fun getInjectee(field: FieldMirror): Injectee {
    return newInjectee(field.signature.type, field)
  }

  private fun newInjectee(type: GenericType, holder: Annotated): Injectee {
    val qualifier = findQualifier(holder)
    val dependency = type.toDependency(qualifier)
    val converter = type.getConverter()
    return Injectee(dependency, converter, holder.annotations)
  }

  private fun GenericType.getConverter(): Converter {
    return when (rawType) {
      Types.PROVIDER_TYPE -> Converter.Identity
      Types.LAZY_TYPE -> Converter.Adapter(LightsaberTypes.LAZY_ADAPTER_TYPE)
      else -> Converter.Instance
    }
  }

  private fun GenericType.toDependency(qualifier: AnnotationMirror?): Dependency {
    when (rawType) {
      Types.PROVIDER_TYPE,
      Types.LAZY_TYPE ->
        if (this is GenericType.Parameterized) {
          return Dependency(typeArguments[0], qualifier)
        } else {
          throw ProcessingException("Type $this must be parameterized")
        }
    }

    return Dependency(this, qualifier)
  }
}
