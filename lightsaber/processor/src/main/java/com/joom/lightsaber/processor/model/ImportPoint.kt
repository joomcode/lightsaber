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

import com.joom.grip.mirrors.AnnotationMirror
import com.joom.grip.mirrors.FieldMirror
import com.joom.grip.mirrors.MethodMirror
import com.joom.grip.mirrors.Type

sealed class ImportPoint {
  abstract val converter: Converter

  data class Method(
    val method: MethodMirror,
    override val converter: Converter
  ) : ImportPoint()

  data class Field(
    val field: FieldMirror,
    override val converter: Converter
  ) : ImportPoint()

  data class Annotation(
    val annotation: AnnotationMirror,
    val importerType: Type.Object,
    val importeeType: Type.Object
  ) : ImportPoint() {
    override val converter: Converter
      get() = Converter.Instance
  }

  sealed class Converter {
    object Instance : Converter()
    data class Adapter(val adapterType: Type.Object) : Converter()
  }

}
