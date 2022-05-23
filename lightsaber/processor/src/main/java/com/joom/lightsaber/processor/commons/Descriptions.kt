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

import com.joom.grip.mirrors.AnnotationMirror
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.EnumMirror
import com.joom.grip.mirrors.FieldMirror
import com.joom.grip.mirrors.MethodMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.signature.GenericType
import com.joom.lightsaber.processor.model.Dependency

fun Dependency.getDescription(): String {
  return if (qualifier == null) type.getDescription() else buildString { appendDescriptionTo(this) }
}

fun Dependency.appendDescriptionTo(appendable: Appendable) {
  if (qualifier != null) {
    qualifier.appendDescriptionTo(appendable)
    appendable.append(" ")
  }

  type.appendDescriptionTo(appendable)
}

fun AnnotationMirror.getDescription(): String {
  return buildString { appendDescriptionTo(this) }
}

fun AnnotationMirror.appendDescriptionTo(appendable: Appendable) {
  appendable.append("@")
  appendAnnotationMirrorTo(appendable, this)
}

fun FieldMirror.getDescription(): String {
  return buildString { appendDescriptionTo(this) }
}

fun FieldMirror.appendDescriptionTo(appendable: Appendable) {
  signature.type.appendDescriptionTo(appendable)
  appendable.append(" ")
  appendable.append(name)
}

fun MethodMirror.getDescription(): String {
  return buildString { appendDescriptionTo(this) }
}

fun MethodMirror.appendDescriptionTo(appendable: Appendable) {
  signature.returnType.appendDescriptionTo(appendable)
  appendable.append(" ")
  appendable.append(name)
  appendable.append("(")
  signature.parameterTypes.forEachIndexed { index, parameterType ->
    if (index != 0) {
      appendable.append(", ")
      parameterType.appendDescriptionTo(appendable)
    }
  }
  appendable.append(")")
}

fun ClassMirror.getDescription(): String {
  return type.getDescription()
}

fun ClassMirror.appendDescriptionTo(appendable: Appendable) {
  appendable.append(getDescription())
}

fun GenericType.getDescription(): String {
  return toString()
}

fun GenericType.appendDescriptionTo(appendable: Appendable) {
  appendable.append(getDescription())
}

fun Type.getDescription(): String {
  return className
}

fun Type.appendDescriptionTo(appendable: Appendable) {
  appendable.append(getDescription())
}

private fun appendAnnotationMirrorTo(appendable: Appendable, mirror: AnnotationMirror) {
  mirror.type.appendDescriptionTo(appendable)
  appendable.append("(")
  mirror.values.entries.forEachIndexed { index, (name, value) ->
    if (index > 0) {
      appendable.append(", ")
    }
    appendable.append(name)
    appendable.append(" = ")
    appendAnnotationValueTo(appendable, value)
  }
  appendable.append(")")
}

private fun appendAnnotationValueTo(appendable: Appendable, value: Any) {
  when (value) {
    is String -> appendable.append("\"").append(value).append("\"")
    is Type -> appendable.append(value.getDescription())
    is List<*> -> appendAnnotationArrayValueTo(appendable, value)
    is EnumMirror -> appendable.append(value.value)
    is AnnotationMirror -> appendAnnotationMirrorTo(appendable, value)
    else -> appendable.append(value.toString())
  }
}

private fun appendAnnotationArrayValueTo(appendable: Appendable, array: List<*>) {
  if (array.isEmpty()) {
    appendable.append("{}")
  }

  appendable.append("{")
  array.forEachIndexed { index, value ->
    if (index > 0) {
      appendable.append(", ")
    }

    if (value != null) {
      appendAnnotationValueTo(appendable, value)
    } else {
      appendable.append("null")
    }
  }
  appendable.append("}")
}
