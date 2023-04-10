/*
 * Copyright 2022 SIA Joom
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

package com.joom.lightsaber.processor.validation

import com.joom.grip.ClassesResult
import com.joom.grip.Grip
import com.joom.grip.Query
import com.joom.grip.annotatedWith
import com.joom.grip.classes
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.superType
import com.joom.grip.withField
import com.joom.grip.withMethod
import com.joom.lightsaber.LightsaberTypes
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.model.Factory
import java.nio.file.Path

class UsageValidator(
  private val grip: Grip,
  private val errorReporter: ErrorReporter,
) {

  fun validateUsage(paths: Collection<Path>) {
    val modulesQuery = grip select classes from paths where annotatedWith(Types.MODULE_TYPE)
    val componentsQuery = grip select classes from paths where annotatedWith(Types.COMPONENT_TYPE)
    val contractConfigurationsQuery = grip select classes from paths where superType { _, type -> type == Types.CONTRACT_CONFIGURATION_TYPE }
    val fieldsQuery = grip select classes from paths where withField { _, fieldMirror -> fieldMirror.annotations.contains(Types.INJECT_TYPE) }
    val methodsQuery = grip select classes from paths where withMethod { _, methodMirror -> methodMirror.annotations.contains(Types.INJECT_TYPE) }
    val factoriesQuery = grip select classes from paths where annotatedWith(Types.FACTORY_TYPE)

    execute(factoriesQuery).forEach { (type, _) ->
      val implementationType = Factory.computeImplementationType(type)

      if (!grip.fileRegistry.contains(implementationType)) {
        reportError(type)
      }
    }

    execute(
      modulesQuery,
      componentsQuery,
      contractConfigurationsQuery,
    ).forEach { (type, mirror) ->
      if (!mirror.interfaces.contains(LightsaberTypes.INJECTOR_CONFIGURATOR_TYPE)) {
        reportError(type)
      }
    }

    execute(
      methodsQuery,
      fieldsQuery
    ).forEach { (type, mirror) ->
      if (!mirror.interfaces.contains(LightsaberTypes.MEMBERS_INJECTOR_TYPE)) {
        reportError(type)
      }
    }
  }

  private fun reportError(type: Type) {
    errorReporter.reportError("Class ${type.className} is not processed by lightsaber, is plugin applied to module?")
  }

  private fun execute(vararg queries: Query<ClassesResult>): Map<Type.Object, ClassMirror> {
    return queries.map {
      @Suppress("USELESS_CAST")
      it.execute() as Map<Type.Object, ClassMirror>
    }.reduce { acc, result -> acc + result }
  }
}
