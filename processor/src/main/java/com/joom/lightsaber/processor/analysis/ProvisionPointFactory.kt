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

import com.joom.lightsaber.processor.commons.contains
import com.joom.lightsaber.processor.commons.toFieldDescriptor
import com.joom.lightsaber.processor.commons.toMethodDescriptor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.model.ProvisionPoint
import com.joom.lightsaber.processor.model.Scope
import io.michaelrocks.grip.Grip
import io.michaelrocks.grip.mirrors.FieldMirror
import io.michaelrocks.grip.mirrors.MethodMirror
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.isConstructor
import io.michaelrocks.grip.mirrors.signature.GenericType
import org.objectweb.asm.Opcodes

interface ProvisionPointFactory {
  fun newConstructorProvisionPoint(target: InjectionTarget): ProvisionPoint.Constructor
  fun newMethodProvisionPoint(container: Type.Object, method: MethodMirror): ProvisionPoint.Method
  fun newFieldProvisionPoint(container: Type.Object, field: FieldMirror): ProvisionPoint.Field
}

class ProvisionPointFactoryImpl(
  private val grip: Grip,
  private val analyzerHelper: AnalyzerHelper,
  private val bridgeRegistry: BridgeRegistry
) : ProvisionPointFactory {

  override fun newConstructorProvisionPoint(target: InjectionTarget): ProvisionPoint.Constructor {
    val mirror = grip.classRegistry.getClassMirror(target.type)
    val dependency = Dependency(GenericType.Raw(target.type), analyzerHelper.findQualifier(mirror))
    val scope = analyzerHelper.findScope(mirror)
    val injectionPoint = target.injectionPoints.first() as InjectionPoint.Method
    check(injectionPoint.method.isConstructor)
    return ProvisionPoint.Constructor(dependency, scope, injectionPoint)
  }

  override fun newMethodProvisionPoint(container: Type.Object, method: MethodMirror): ProvisionPoint.Method {
    val dependency = Dependency(method.signature.returnType, analyzerHelper.findQualifier(method))
    val scope = analyzerHelper.findScope(method)
    val injectionPoint = analyzerHelper.convertMethodToInjectionPoint(method, container)
    check(!injectionPoint.method.isConstructor)
    return ProvisionPoint.Method(dependency, scope, injectionPoint, null).withBridge()
  }

  override fun newFieldProvisionPoint(container: Type.Object, field: FieldMirror): ProvisionPoint.Field {
    val dependency = Dependency(field.signature.type, analyzerHelper.findQualifier(field))
    val scope = analyzerHelper.findScope(field)
    return ProvisionPoint.Field(container, dependency, scope, null, field).withBridge()
  }

  private fun ProvisionPoint.Method.withBridge(): ProvisionPoint.Method {
    val method = injectionPoint.method
    if (Opcodes.ACC_PRIVATE !in method.access) {
      return this
    }

    val bridgeMethod = bridgeRegistry.addBridge(containerType, method)
    val bridgeInjectionPoint = injectionPoint.copy(method = bridgeMethod)
    val bridgeProvisionPoint = ProvisionPoint.Method(dependency, Scope.None, bridgeInjectionPoint, null)
    return copy(bridge = bridgeProvisionPoint)
  }

  private fun ProvisionPoint.Field.withBridge(): ProvisionPoint.Field {
    val field = field
    if (Opcodes.ACC_PRIVATE !in field.access) {
      return this
    }

    val bridgeMethod = bridgeRegistry.addBridge(containerType, field)
    val bridgeInjectionPoint = InjectionPoint.Method(containerType, bridgeMethod, listOf())
    val bridgeProvisionPoint = ProvisionPoint.Method(dependency, Scope.None, bridgeInjectionPoint, null)
    return copy(bridge = bridgeProvisionPoint)
  }

  private fun BridgeRegistry.addBridge(type: Type.Object, method: MethodMirror): MethodMirror {
    val bridge = addBridge(type, method.toMethodDescriptor())
    return createBridgeMirror(bridge)
  }

  private fun BridgeRegistry.addBridge(type: Type.Object, field: FieldMirror): MethodMirror {
    val bridge = addBridge(type, field.toFieldDescriptor())
    return createBridgeMirror(bridge)
  }

  private fun createBridgeMirror(bridge: MethodDescriptor): MethodMirror {
    return MethodMirror.Builder()
      .access(Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC)
      .name(bridge.name)
      .type(bridge.type)
      .build()
  }
}
