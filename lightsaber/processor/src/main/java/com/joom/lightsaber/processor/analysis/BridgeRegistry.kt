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

import com.joom.grip.ClassRegistry
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.getMethodType
import com.joom.lightsaber.processor.commons.toMethodDescriptor
import com.joom.lightsaber.processor.descriptors.FieldDescriptor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor

interface BridgeRegistry {
  fun addBridge(type: Type.Object, field: FieldDescriptor): MethodDescriptor
  fun addBridge(type: Type.Object, method: MethodDescriptor): MethodDescriptor
  fun getBridges(type: Type.Object): Collection<Map.Entry<Any, MethodDescriptor>>
  fun getBridge(type: Type.Object, descriptor: Any): MethodDescriptor?
}

class BridgeRegistryImpl(
  private val classRegistry: ClassRegistry
) : BridgeRegistry {

  private val classMirrorBridgeRegistryByType = mutableMapOf<Type.Object, ClassMirrorBridgeRegistry>()

  override fun addBridge(type: Type.Object, field: FieldDescriptor): MethodDescriptor {
    return getOrCreateClassMirrorBridgeRegistry(type).addBridge(field)
  }

  override fun addBridge(type: Type.Object, method: MethodDescriptor): MethodDescriptor {
    return getOrCreateClassMirrorBridgeRegistry(type).addBridge(method)
  }

  override fun getBridges(type: Type.Object): Collection<Map.Entry<Any, MethodDescriptor>> {
    return getClassMirrorBridgeRegistry(type)?.getBridges().orEmpty()
  }

  override fun getBridge(type: Type.Object, descriptor: Any): MethodDescriptor? {
    return getClassMirrorBridgeRegistry(type)?.getBridge(descriptor)
  }

  private fun getOrCreateClassMirrorBridgeRegistry(type: Type.Object): ClassMirrorBridgeRegistry {
    return classMirrorBridgeRegistryByType.getOrPut(type) {
      ClassMirrorBridgeRegistry(classRegistry.getClassMirror(type))
    }
  }

  private fun getClassMirrorBridgeRegistry(type: Type.Object): ClassMirrorBridgeRegistry? {
    return classMirrorBridgeRegistryByType[type]
  }

  private class ClassMirrorBridgeRegistry(mirror: ClassMirror) {
    private val reservedMethods = mutableSetOf<MethodDescriptor>()
    private val descriptorToBridgeMap = mutableMapOf<Any, MethodDescriptor>()

    init {
      mirror.methods.forEach { method ->
        reservedMethods += method.toMethodDescriptor()
      }
    }

    fun addBridge(field: FieldDescriptor): MethodDescriptor {
      return descriptorToBridgeMap.getOrPut(field) { createBridgeMethod(field) }
    }

    fun addBridge(method: MethodDescriptor): MethodDescriptor {
      return descriptorToBridgeMap.getOrPut(method) { createBridgeMethod(method) }
    }

    fun getBridges(): Collection<Map.Entry<Any, MethodDescriptor>> {
      return descriptorToBridgeMap.entries
    }

    fun getBridge(descriptor: Any): MethodDescriptor? {
      return descriptorToBridgeMap[descriptor]
    }

    private fun createBridgeMethod(field: FieldDescriptor): MethodDescriptor {
      return reserveBridge(field.name, getMethodType(field.type))
    }

    private fun createBridgeMethod(method: MethodDescriptor): MethodDescriptor {
      return reserveBridge(method.name, method.type)
    }

    private fun reserveBridge(baseName: String, methodType: Type.Method): MethodDescriptor {
      for (index in 0..Int.MAX_VALUE) {
        val bridgeName = "$baseName\$Bridge$index"
        val bridge = MethodDescriptor(bridgeName, methodType)
        if (reservedMethods.add(bridge)) {
          return bridge
        }
      }

      error("Cannot reserve a bridge name for $baseName")
    }
  }
}
