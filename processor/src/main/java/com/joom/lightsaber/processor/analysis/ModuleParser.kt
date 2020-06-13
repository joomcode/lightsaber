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

import com.joom.lightsaber.processor.ProcessingException
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.contains
import com.joom.lightsaber.processor.commons.toFieldDescriptor
import com.joom.lightsaber.processor.commons.toMethodDescriptor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.logging.getLogger
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import com.joom.lightsaber.processor.model.Scope
import io.michaelrocks.grip.Grip
import io.michaelrocks.grip.and
import io.michaelrocks.grip.annotatedWith
import io.michaelrocks.grip.fields
import io.michaelrocks.grip.from
import io.michaelrocks.grip.isStatic
import io.michaelrocks.grip.methodType
import io.michaelrocks.grip.methods
import io.michaelrocks.grip.mirrors.ClassMirror
import io.michaelrocks.grip.mirrors.FieldMirror
import io.michaelrocks.grip.mirrors.MethodMirror
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.signature.GenericType
import io.michaelrocks.grip.not
import io.michaelrocks.grip.returns
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC

interface ModuleParser {
  fun parseModule(
    type: Type.Object,
    importeeModuleTypes: Collection<Type.Object>,
    providableTargets: Collection<InjectionTarget>,
    factories: Collection<Factory>,
    contracts: Collection<Contract>,
    moduleRegistry: ModuleRegistry
  ): Module
}

class ModuleParserImpl(
  private val grip: Grip,
  private val importParser: ImportParser,
  private val bindingRegistry: BindingRegistry,
  private val analyzerHelper: AnalyzerHelper
) : ModuleParser {

  private val logger = getLogger()

  private val bridgeRegistry = BridgeRegistry()

  override fun parseModule(
    type: Type.Object,
    importeeModuleTypes: Collection<Type.Object>,
    providableTargets: Collection<InjectionTarget>,
    factories: Collection<Factory>,
    contracts: Collection<Contract>,
    moduleRegistry: ModuleRegistry
  ): Module {
    return parseModule(
      grip.classRegistry.getClassMirror(type),
      importeeModuleTypes,
      providableTargets,
      factories,
      contracts,
      moduleRegistry
    )
  }

  private fun parseModule(
    mirror: ClassMirror,
    importeeModuleTypes: Collection<Type.Object>,
    providableTargets: Collection<InjectionTarget>,
    factories: Collection<Factory>,
    contracts: Collection<Contract>,
    moduleRegistry: ModuleRegistry
  ): Module {
    if (mirror.signature.typeVariables.isNotEmpty()) {
      throw ModuleParserException("Module cannot have a type parameters: ${mirror.type.className}")
    }

    val imports = importParser.parseImports(mirror, moduleRegistry, importeeModuleTypes)

    bridgeRegistry.clear()
    mirror.methods.forEach { bridgeRegistry.reserveMethod(it.toMethodDescriptor()) }

    val provisionPoints = createProvisionPoints(mirror, providableTargets)
    val dependencies = provisionPoints.map { it.dependency } + factories.map { it.dependency } + contracts.map { it.dependency }
    val bindings = dependencies.flatMap { bindingRegistry.findBindingsByDependency(it) }
    return Module(mirror.type, imports, provisionPoints, bindings, factories, contracts)
  }

  private fun createProvisionPoints(mirror: ClassMirror, providableTargets: Collection<InjectionTarget>): Collection<ProvisionPoint> {
    val isProvidable = annotatedWith(Types.PROVIDE_TYPE) and not(isStatic())
    val methodsQuery = grip select methods from mirror where (isProvidable and methodType(not(returns(Type.Primitive.Void))))
    val fieldsQuery = grip select fields from mirror where isProvidable

    val provisionPoints = ArrayList<ProvisionPoint>()

    logger.debug("Module: {}", mirror.type.className)
    providableTargets.mapTo(provisionPoints) { target ->
      logger.debug("  Constructor: {}", target.injectionPoints.first())
      newConstructorProvisionPoint(target)
    }

    methodsQuery.execute()[mirror.type].orEmpty().mapTo(provisionPoints) { method ->
      logger.debug("  Method: {}", method)
      newMethodProvisionPoint(mirror.type, method)
    }

    fieldsQuery.execute()[mirror.type].orEmpty().mapTo(provisionPoints) { field ->
      logger.debug("  Field: {}", field)
      newFieldProvisionPoint(mirror.type, field)
    }

    return provisionPoints
  }

  private fun newConstructorProvisionPoint(target: InjectionTarget): ProvisionPoint.Constructor {
    val mirror = grip.classRegistry.getClassMirror(target.type)
    val dependency = Dependency(GenericType.Raw(target.type), analyzerHelper.findQualifier(mirror))
    val scope = analyzerHelper.findScope(mirror)
    val injectionPoint = target.injectionPoints.first() as InjectionPoint.Method
    return ProvisionPoint.Constructor(dependency, scope, injectionPoint)
  }

  private fun newMethodProvisionPoint(container: Type.Object, method: MethodMirror): ProvisionPoint.Method {
    val dependency = Dependency(method.signature.returnType, analyzerHelper.findQualifier(method))
    val scope = analyzerHelper.findScope(method)
    val injectionPoint = analyzerHelper.convertMethodToInjectionPoint(method, container)
    return ProvisionPoint.Method(dependency, scope, injectionPoint, null).withBridge()
  }

  private fun newFieldProvisionPoint(container: Type.Object, field: FieldMirror): ProvisionPoint.Field {
    val dependency = Dependency(field.signature.type, analyzerHelper.findQualifier(field))
    val scope = analyzerHelper.findScope(field)
    return ProvisionPoint.Field(container, dependency, scope, null, field).withBridge()
  }

  private fun ProvisionPoint.Method.withBridge(): ProvisionPoint.Method {
    val method = injectionPoint.method
    if (ACC_PRIVATE !in method.access) {
      return this
    }

    val bridgeMethod = bridgeRegistry.addBridge(method)
    val bridgeInjectionPoint = injectionPoint.copy(method = bridgeMethod)
    val bridgeProvisionPoint = ProvisionPoint.Method(dependency, Scope.None, bridgeInjectionPoint, null)
    return copy(bridge = bridgeProvisionPoint)
  }

  private fun ProvisionPoint.Field.withBridge(): ProvisionPoint.Field {
    val field = field
    if (ACC_PRIVATE !in field.access) {
      return this
    }

    val bridgeMethod = bridgeRegistry.addBridge(field)
    val bridgeInjectionPoint = InjectionPoint.Method(containerType, bridgeMethod, listOf())
    val bridgeProvisionPoint = ProvisionPoint.Method(dependency, Scope.None, bridgeInjectionPoint, null)
    return copy(bridge = bridgeProvisionPoint)
  }

  private fun BridgeRegistry.addBridge(method: MethodMirror): MethodMirror {
    val bridge = addBridge(method.toMethodDescriptor())
    return createBridgeMirror(bridge)
  }

  private fun BridgeRegistry.addBridge(field: FieldMirror): MethodMirror {
    val bridge = addBridge(field.toFieldDescriptor())
    return createBridgeMirror(bridge)
  }

  private fun createBridgeMirror(bridge: MethodDescriptor): MethodMirror {
    return MethodMirror.Builder()
      .access(ACC_PUBLIC or ACC_SYNTHETIC)
      .name(bridge.name)
      .type(bridge.type)
      .build()
  }
}

class ModuleParserException(message: String) : ProcessingException(message)
