/*
 * Copyright 2016 Michael Rozumyanskiy
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

package io.michaelrocks.lightsaber.processor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM5

open class ProcessorMethodVisitor @JvmOverloads constructor(
    val errorReporter: ErrorReporter,
    methodVisitor: MethodVisitor? = null
) : MethodVisitor(ASM5, methodVisitor) {

  fun reportError(errorMessage: String) {
    reportError(ProcessingException(errorMessage))
  }

  fun reportError(error: Exception) {
    errorReporter.reportError(error)
  }
}
