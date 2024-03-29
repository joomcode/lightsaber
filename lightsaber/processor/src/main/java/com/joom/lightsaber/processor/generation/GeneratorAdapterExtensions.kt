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

package com.joom.lightsaber.processor.generation

import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.signature.GenericType
import com.joom.lightsaber.LightsaberTypes
import com.joom.lightsaber.processor.commons.GeneratorAdapter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.boxed
import com.joom.lightsaber.processor.commons.rawType
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.generation.model.Key
import com.joom.lightsaber.processor.generation.model.KeyRegistry
import com.joom.lightsaber.processor.generation.model.Provider
import com.joom.lightsaber.processor.generation.model.scope
import com.joom.lightsaber.processor.model.Converter
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Injectee
import com.joom.lightsaber.processor.model.Scope

private val ADAPTER_CONSTRUCTOR = MethodDescriptor.forConstructor(Types.PROVIDER_TYPE)

private val GET_PROVIDER_FOR_CLASS_METHOD =
  MethodDescriptor.forMethod("getProvider", Types.PROVIDER_TYPE, Types.CLASS_TYPE)
private val GET_PROVIDER_FOR_TYPE_METHOD =
  MethodDescriptor.forMethod("getProvider", Types.PROVIDER_TYPE, Types.TYPE_TYPE)
private val GET_PROVIDER_FOR_KEY_METHOD =
  MethodDescriptor.forMethod("getProvider", Types.PROVIDER_TYPE, Types.KEY_TYPE)

private val GET_INSTANCE_FOR_CLASS_METHOD =
  MethodDescriptor.forMethod("getInstance", Types.OBJECT_TYPE, Types.CLASS_TYPE)
private val GET_INSTANCE_FOR_TYPE_METHOD =
  MethodDescriptor.forMethod("getInstance", Types.OBJECT_TYPE, Types.TYPE_TYPE)
private val GET_INSTANCE_FOR_KEY_METHOD =
  MethodDescriptor.forMethod("getInstance", Types.OBJECT_TYPE, Types.KEY_TYPE)

private val REGISTER_PROVIDER_FOR_CLASS_METHOD =
  MethodDescriptor.forMethod("registerProvider", Type.Primitive.Void, Types.CLASS_TYPE, Types.PROVIDER_TYPE)
private val REGISTER_PROVIDER_FOR_TYPE_METHOD =
  MethodDescriptor.forMethod("registerProvider", Type.Primitive.Void, Types.TYPE_TYPE, Types.PROVIDER_TYPE)
private val REGISTER_PROVIDER_FOR_KEY_METHOD =
  MethodDescriptor.forMethod("registerProvider", Type.Primitive.Void, Types.KEY_TYPE, Types.PROVIDER_TYPE)

private val DELEGATE_PROVIDER_CONSTRUCTOR = MethodDescriptor.forConstructor(Types.PROVIDER_TYPE)

fun GeneratorAdapter.getDependency(keyRegistry: KeyRegistry, injectee: Injectee) {
  when (injectee.converter) {
    is Converter.Identity -> {
      getProvider(keyRegistry, injectee.dependency)
    }

    is Converter.Instance -> {
      if (injectee.dependency.type.rawType != Types.INJECTOR_TYPE || injectee.dependency.qualifier != null) {
        getInstance(keyRegistry, injectee.dependency)
        unbox(injectee.dependency.type.rawType)
      }
    }

    is Converter.Adapter -> {
      getProvider(keyRegistry, injectee.dependency)
      newInstance(injectee.converter.adapterType)
      dupX1()
      swap()
      invokeConstructor(injectee.converter.adapterType, ADAPTER_CONSTRUCTOR)
    }
  }
}

fun GeneratorAdapter.getProvider(keyRegistry: KeyRegistry, dependency: Dependency) {
  val key = pushTypeOrKey(keyRegistry, dependency)

  when (key) {
    null -> invokeInterface(Types.INJECTOR_TYPE, GET_PROVIDER_FOR_CLASS_METHOD)
    is Key.Type -> invokeInterface(Types.INJECTOR_TYPE, GET_PROVIDER_FOR_TYPE_METHOD)
    is Key.QualifiedType -> invokeInterface(Types.INJECTOR_TYPE, GET_PROVIDER_FOR_KEY_METHOD)
  }
}

fun GeneratorAdapter.getInstance(keyRegistry: KeyRegistry, dependency: Dependency) {
  val key = pushTypeOrKey(keyRegistry, dependency)

  when (key) {
    null -> invokeInterface(Types.INJECTOR_TYPE, GET_INSTANCE_FOR_CLASS_METHOD)
    is Key.Type -> invokeInterface(Types.INJECTOR_TYPE, GET_INSTANCE_FOR_TYPE_METHOD)
    is Key.QualifiedType -> invokeInterface(Types.INJECTOR_TYPE, GET_INSTANCE_FOR_KEY_METHOD)
  }
}

fun GeneratorAdapter.registerProvider(keyRegistry: KeyRegistry, provider: Provider, providerCreator: () -> Unit) {
  val key = pushTypeOrKey(keyRegistry, provider.dependency)

  when (val scope = provider.scope) {
    is Scope.Class -> newDelegator(scope.scopeType, providerCreator)
    is Scope.None -> providerCreator()
  }

  when (key) {
    null -> invokeVirtual(LightsaberTypes.LIGHTSABER_INJECTOR_TYPE, REGISTER_PROVIDER_FOR_CLASS_METHOD)
    is Key.Type -> invokeVirtual(LightsaberTypes.LIGHTSABER_INJECTOR_TYPE, REGISTER_PROVIDER_FOR_TYPE_METHOD)
    is Key.QualifiedType -> invokeVirtual(LightsaberTypes.LIGHTSABER_INJECTOR_TYPE, REGISTER_PROVIDER_FOR_KEY_METHOD)
  }
}

private fun GeneratorAdapter.newDelegator(scopeType: Type, providerCreator: () -> Unit) {
  newInstance(scopeType)
  dup()
  providerCreator()
  invokeConstructor(scopeType, DELEGATE_PROVIDER_CONSTRUCTOR)
}

private fun GeneratorAdapter.pushTypeOrKey(keyRegistry: KeyRegistry, dependency: Dependency): Key? {
  val key = keyRegistry.keys[dependency.boxed()]
  if (key == null) {
    push(dependency.type)
  } else {
    getStatic(keyRegistry.type, key.field)
  }
  return key
}

private fun GeneratorAdapter.push(type: GenericType) {
  when (type) {
    is GenericType.Raw -> push(type.type.boxed())
    else -> error("Cannot push a generic type $type")
  }
}
