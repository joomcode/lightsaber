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

import com.joom.grip.FieldsResult
import com.joom.grip.Grip
import com.joom.grip.MethodsResult
import com.joom.grip.annotatedWith
import com.joom.grip.fields
import com.joom.grip.methods
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.isConstructor
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.cast
import com.joom.lightsaber.processor.commons.given
import com.joom.lightsaber.processor.logging.getLogger
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import java.io.File

interface InjectionTargetsAnalyzer {
  fun analyze(files: Collection<File>): Result

  data class Result(
    val injectableTargets: Collection<InjectionTarget>,
    val providableTargets: Collection<InjectionTarget>
  )
}

class InjectionTargetsAnalyzerImpl(
  private val grip: Grip,
  private val analyzerHelper: AnalyzerHelper,
  private val errorReporter: ErrorReporter
) : InjectionTargetsAnalyzer {

  private val logger = getLogger()

  override fun analyze(files: Collection<File>): InjectionTargetsAnalyzer.Result {
    val context = createInjectionTargetsContext(files)
    val injectableTargets = analyzeInjectableTargets(context)
    val providableTargets = analyzeProvidableTargets(context)
    return InjectionTargetsAnalyzer.Result(injectableTargets, providableTargets)
  }

  private fun createInjectionTargetsContext(files: Collection<File>): InjectionTargetsContext {
    val methodsQuery = grip select methods from files where annotatedWith(Types.INJECT_TYPE)
    val fieldsQuery = grip select fields from files where annotatedWith(Types.INJECT_TYPE)

    val methodsResult = methodsQuery.execute()
    val fieldsResult = fieldsQuery.execute()

    val types = HashSet<Type.Object>(methodsResult.size + fieldsResult.size).apply {
      addAll(methodsResult.types)
      addAll(fieldsResult.types)
    }

    return InjectionTargetsContext(types, methodsResult, fieldsResult)
  }

  private fun analyzeInjectableTargets(context: InjectionTargetsContext): Collection<InjectionTarget> {
    return context.types.mapNotNull { type ->
      logger.debug("Target: {}", type)
      val injectionPoints = ArrayList<InjectionPoint>()

      context.methods[type]?.mapNotNullTo(injectionPoints) { method ->
        logger.debug("  Method: {}", method)
        given(!method.isConstructor) { analyzerHelper.convertMethodToInjectionPoint(method, type) }
      }

      context.fields[type]?.mapTo(injectionPoints) { field ->
        logger.debug("  Field: {}", field)
        analyzerHelper.convertFieldToInjectionPoint(field, type)
      }

      given(injectionPoints.isNotEmpty()) { InjectionTarget(type, injectionPoints) }
    }
  }

  private fun analyzeProvidableTargets(context: InjectionTargetsContext): Collection<InjectionTarget> {
    return context.types.mapNotNull { type ->
      logger.debug("Target: {}", type)
      val constructors = context.methods[type].orEmpty().mapNotNull { method ->
        logger.debug("  Method: {}", method)
        given(method.isConstructor) { analyzerHelper.convertMethodToInjectionPoint(method, type) }
      }

      given(constructors.isNotEmpty()) {
        if (constructors.size > 1) {
          val separator = "\n  "
          val constructorsString = constructors.map { it.cast<InjectionPoint.Method>().method }.joinToString(separator)
          errorReporter.reportError("Class has multiple injectable constructors:$separator$constructorsString")
        }

        InjectionTarget(type, constructors)
      }
    }
  }

  private class InjectionTargetsContext(
    val types: Collection<Type.Object>,
    val methods: MethodsResult,
    val fields: FieldsResult
  )
}
