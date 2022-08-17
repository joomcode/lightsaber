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
import com.joom.grip.from
import com.joom.grip.isConstructor
import com.joom.grip.methods
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.MethodMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.signature.GenericType
import com.joom.lightsaber.Factory.Return
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.associateByIndexedTo
import com.joom.lightsaber.processor.commons.boxed
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.FactoryInjectee
import com.joom.lightsaber.processor.model.FactoryInjectionPoint
import com.joom.lightsaber.processor.model.FactoryProvisionPoint
import com.joom.lightsaber.processor.model.Injectee
import com.joom.lightsaber.processor.model.InjectionPoint

interface FactoryParser {
  fun parseFactory(type: Type.Object): Factory
}

class FactoryParserImpl(
  private val grip: Grip,
  private val analyzerHelper: AnalyzerHelper,
  private val errorReporter: ErrorReporter,
) : FactoryParser {

  private val factoriesByType = mutableMapOf<Type.Object, Factory>()

  override fun parseFactory(type: Type.Object): Factory {
    return factoriesByType.getOrPut(type) {
      val mirror = grip.classRegistry.getClassMirror(type)
      parseFactory(mirror)
    }
  }

  private fun parseFactory(mirror: ClassMirror): Factory {
    val implementationType = Factory.computeImplementationType(mirror.type)
    val qualifier = analyzerHelper.findQualifier(mirror)
    val dependency = Dependency(GenericType.Raw(mirror.type), qualifier)
    val provisionPoints = mirror.methods.mapNotNull { maybeCreateFactoryProvisionPoint(mirror, it) }
    return Factory(mirror.type, implementationType, dependency, provisionPoints)
  }

  private fun maybeCreateFactoryProvisionPoint(mirror: ClassMirror, method: MethodMirror): FactoryProvisionPoint? {
    val returnType = tryExtractReturnTypeFromFactoryMethod(mirror, method) ?: return null

    val dependencyMirror = grip.classRegistry.getClassMirror(returnType)
    val dependencyConstructorsQuery =
      grip select methods from dependencyMirror where (isConstructor() and annotatedWith(Types.FACTORY_INJECT_TYPE))
    val dependencyConstructors = dependencyConstructorsQuery.execute().values.singleOrNull().orEmpty()
    if (dependencyConstructors.isEmpty()) {
      error("Class ${dependencyMirror.type.className} must have a constructor annotated with @Factory.Inject")
      return null
    }

    if (dependencyConstructors.size != 1) {
      error("Class ${dependencyMirror.type.className} must have a single constructor annotated with @Factory.Inject")
      return null
    }

    val methodInjectionPoint = analyzerHelper.convertMethodToInjectionPoint(method, mirror.type)
    validateNoDuplicateInjectees(methodInjectionPoint)
    val argumentIndexToInjecteeMap = methodInjectionPoint.injectees.associateByIndexedTo(
      HashMap(),
      keySelector = { _, injectee -> injectee },
      valueSelector = { index, _ -> index }
    )

    val constructor = dependencyConstructors.single()
    val constructorInjectionPoint = analyzerHelper.convertMethodToInjectionPoint(constructor, mirror.type)

    val factoryInjectees = constructorInjectionPoint.injectees.mapNotNull { injectee ->
      if (Types.FACTORY_PARAMETER_TYPE in injectee.annotations) {
        val argumentIndex = argumentIndexToInjecteeMap[injectee]
        if (argumentIndex == null) {
          val dependencyClassName = dependencyMirror.type.className
          val factoryClassName = mirror.type.className
          error("Class $dependencyClassName contains a @Factory.Parameter not provided by factory $factoryClassName: ${injectee.dependency}")
          null
        } else {
          FactoryInjectee.FromMethod(injectee, argumentIndex)
        }
      } else {
        FactoryInjectee.FromInjector(injectee)
      }
    }

    val factoryInjectionPoint = FactoryInjectionPoint(returnType, constructor, factoryInjectees)
    return FactoryProvisionPoint(mirror.type, method, factoryInjectionPoint)
  }

  private fun tryExtractReturnTypeFromFactoryMethod(mirror: ClassMirror, method: MethodMirror): Type.Object? {
    val returnAnnotation = method.annotations[Types.FACTORY_RETURN_TYPE]
    if (returnAnnotation != null) {
      val returnType = returnAnnotation.values[Return::value.name]
      if (returnType !is Type) {
        error("Method ${mirror.type.className}.${method.name} is annotated with @Factory.Return that has a wrong parameter $returnType")
        return null
      }

      if (returnType !is Type.Object) {
        error("Method ${mirror.type.className}.${method.name} is annotated with @Factory.Return with ${returnType.className} value, but its value must be a class")
        return null
      }

      return returnType
    }

    val returnType = method.type.returnType
    if (returnType !is Type.Object) {
      error("Method ${mirror.type.className}.${method.name} returns ${returnType.className}, but must return a class")
      return null
    }

    return returnType
  }

  private fun validateNoDuplicateInjectees(injectionPoint: InjectionPoint.Method) {
    val visitedInjectees = hashSetOf<Injectee>()
    injectionPoint.injectees.forEach { injectee ->
      if (!visitedInjectees.add(injectee.boxed())) {
        val className = injectionPoint.containerType.className
        val methodName = injectionPoint.method.name
        error("Method $className.$methodName accepts $injectee multiple times")
      }
    }
  }

  private fun error(message: String) {
    errorReporter.reportError(message)
  }
}
