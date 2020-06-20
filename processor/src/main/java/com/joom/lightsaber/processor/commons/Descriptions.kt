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

import com.joom.lightsaber.processor.model.Dependency
import io.michaelrocks.grip.mirrors.AnnotationMirror
import io.michaelrocks.grip.mirrors.ClassMirror
import io.michaelrocks.grip.mirrors.EnumMirror
import io.michaelrocks.grip.mirrors.FieldMirror
import io.michaelrocks.grip.mirrors.MethodMirror
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.signature.GenericType

fun Dependency.getDescription(): String {
  val typeDescription = type.getDescription()
  return if (qualifier == null) typeDescription else "${qualifier.getDescription()} $typeDescription"
}

fun AnnotationMirror.getDescription(): String {
  return buildString {
    append("@")
    appendAnnotationMirror(this@getDescription)
  }
}

fun FieldMirror.getDescription(): String {
  return "${signature.type} $name"
}

fun MethodMirror.getDescription(): String {
  return buildString {
    append(signature.returnType)
    append(" ")
    append(name)
    append("(")
    signature.parameterTypes.joinTo(this)
    append(")")
  }
}

fun ClassMirror.getDescription(): String {
  return type.getDescription()
}

fun GenericType.getDescription(): String {
  return toString()
}

fun Type.getDescription(): String {
  return className
}

private fun StringBuilder.appendAnnotationMirror(mirror: AnnotationMirror): StringBuilder = apply {
  append(mirror.getDescription())
  append("(")
  mirror.values.entries.forEachIndexed { index, (name, value) ->
    if (index > 0) {
      append(", ")
    }
    append(name)
    append(" = ")
    appendAnnotationValue(value)
  }
  append(")")
}

private fun StringBuilder.appendAnnotationValue(value: Any): StringBuilder = apply {
  return when (value) {
    is String -> append("\"").append(value).append("\"")
    is Type -> append(value.getDescription())
    is List<*> -> appendAnnotationArrayValue(value)
    is EnumMirror -> append(value.value)
    is AnnotationMirror -> appendAnnotationMirror(value)
    else -> append(value)
  }
}

private fun StringBuilder.appendAnnotationArrayValue(array: List<*>): StringBuilder = apply {
  if (array.isEmpty()) {
    append("{}")
  }

  append("{")
  array.forEachIndexed { index, value ->
    if (index > 0) {
      append(", ")
    }

    if (value != null) {
      appendAnnotationValue(value)
    } else {
      append("null")
    }
  }
  append("}")
}
