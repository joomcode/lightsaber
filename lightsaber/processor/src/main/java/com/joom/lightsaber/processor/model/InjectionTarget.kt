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

package com.joom.lightsaber.processor.model

import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.isConstructor
import com.joom.lightsaber.processor.commons.toFieldDescriptor
import com.joom.lightsaber.processor.commons.toMethodDescriptor
import com.joom.lightsaber.processor.descriptors.FieldDescriptor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor

data class InjectionTarget(
  val type: Type.Object,
  val injectionPoints: Collection<InjectionPoint>
) {

  private val fields: Set<FieldDescriptor>
  private val methods: Set<MethodDescriptor>
  private val constructors: Set<MethodDescriptor>

  init {
    val constructors = HashSet<MethodDescriptor>()
    val fields = HashSet<FieldDescriptor>()
    val methods = HashSet<MethodDescriptor>()

    injectionPoints.forEach { injectionPoint ->
      when (injectionPoint) {
        is InjectionPoint.Field -> fields += injectionPoint.field.toFieldDescriptor()
        is InjectionPoint.Method ->
          if (injectionPoint.method.isConstructor) constructors += injectionPoint.method.toMethodDescriptor()
          else methods += injectionPoint.method.toMethodDescriptor()
      }
    }

    this.constructors = constructors
    this.fields = fields
    this.methods = methods
  }

  fun isInjectableField(field: FieldDescriptor) = field in fields
  fun isInjectableMethod(method: MethodDescriptor) = method in methods
  fun isInjectableConstructor(method: MethodDescriptor) = method in constructors
}
