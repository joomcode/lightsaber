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

import com.joom.grip.ClassRegistry
import com.joom.grip.mirrors.Annotated
import com.joom.grip.mirrors.AnnotationMirror
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.Element
import com.joom.grip.mirrors.FieldMirror
import com.joom.grip.mirrors.MethodMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.signature.GenericType
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

interface AnalyzerHelper {
  fun convertMethodToInjectionPoint(method: MethodMirror, container: Type.Object): InjectionPoint.Method
  fun convertFieldToInjectionPoint(field: FieldMirror, container: Type.Object): InjectionPoint.Field
  fun convertMethodParameterToInjectee(method: MethodMirror, parameterIndex: Int): Injectee
  fun convertMethodResultToInjectee(method: MethodMirror): Injectee
  fun convertFieldToInjectee(field: FieldMirror): Injectee
  fun findConfigurationContractType(mirror: ClassMirror): Type.Object?
  fun findQualifier(annotated: Annotated): AnnotationMirror?
  fun findScope(annotated: Annotated): Scope
  fun isModule(mirror: ClassMirror): Boolean
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

  override fun findConfigurationContractType(mirror: ClassMirror): Type.Object? {
    val superType = mirror.superType
    if (superType != Types.CONTRACT_CONFIGURATION_TYPE) {
      return null
    }

    val genericSuperType = mirror.signature.superType
    if (genericSuperType !is GenericType.Parameterized) {
      errorReporter.reportError("Invalid base class of ${mirror.type.className}: $genericSuperType")
      return null
    }

    check(genericSuperType.type == Types.CONTRACT_CONFIGURATION_TYPE)
    check(genericSuperType.typeArguments.size == 1)
    val genericContractType = genericSuperType.typeArguments[0]
    if (genericContractType !is GenericType.Raw) {
      errorReporter.reportError("ContractConfiguration ${mirror.type.className} contains a generic type: $genericContractType")
      return null
    }

    val contractType = genericContractType.type
    if (contractType !is Type.Object) {
      errorReporter.reportError("ContractConfiguration ${mirror.type.className} contains a non-class type: $contractType")
      return null
    }

    return contractType
  }

  override fun findQualifier(annotated: Annotated): AnnotationMirror? {
    fun isQualifier(annotationType: Type.Object): Boolean {
      return classRegistry.getClassMirror(annotationType).annotations.contains(Types.QUALIFIER_TYPE)
    }

    val qualifierCount = annotated.annotations.count { isQualifier(it.type) }
    if (qualifierCount > 0) {
      if (qualifierCount > 1) {
        errorReporter.reportError("Element $annotated has multiple qualifiers")
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

    val isEager = Types.EAGER_TYPE in annotated.annotations
    if (isEager && scopeProviders.isEmpty()) {
      errorReporter.reportError("Element ${annotated.name} is annotated with @Eager but doesn't have a scope")
    }

    return when (scopeProviders.size) {
      0 -> Scope.None
      1 -> Scope.Class(scopeProviders[0], isEager)

      else -> {
        errorReporter.reportError("Element ${annotated.name} has multiple scopes: $scopeProviders")
        Scope.None
      }
    }
  }

  override fun isModule(mirror: ClassMirror): Boolean {
    val annotations = mirror.annotations
    if (Types.MODULE_TYPE in annotations || Types.COMPONENT_TYPE in annotations) {
      return true
    }

    if (mirror.superType == Types.CONTRACT_CONFIGURATION_TYPE) {
      return true
    }

    return false
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
    return when (rawType) {
      Types.PROVIDER_TYPE,
      Types.LAZY_TYPE ->
        if (this is GenericType.Parameterized) {
          Dependency(typeArguments[0], qualifier)
        } else {
          throw ProcessingException("Type $this must be parameterized")
        }
      else -> {
        Dependency(this, qualifier)
      }
    }
  }

  private val Annotated.name: String
    get() {
      return when (this) {
        is Element<*> -> this.name
        else -> this.toString()
      }
    }
}
