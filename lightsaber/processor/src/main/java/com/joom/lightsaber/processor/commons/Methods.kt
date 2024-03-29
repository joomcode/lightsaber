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

package com.joom.lightsaber.processor.commons

import com.joom.grip.mirrors.Type
import com.joom.lightsaber.processor.descriptors.MethodDescriptor

object Methods {
  val HASH_CODE_METHOD = MethodDescriptor.forMethod("hashCode", Type.Primitive.Int)
  val EQUALS_METHOD = MethodDescriptor.forMethod("equals", Type.Primitive.Boolean, Types.OBJECT_TYPE)
  val TO_STRING_METHOD = MethodDescriptor.forMethod("toString", Types.STRING_TYPE)

  val GET_VALUE_METHOD = MethodDescriptor.forMethod("getValue", Types.OBJECT_TYPE)
  val GET_METHOD = MethodDescriptor.forMethod("get", Types.OBJECT_TYPE)
}
