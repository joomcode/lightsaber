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

import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractProvisionPoint
import com.joom.lightsaber.processor.model.Dependency
import io.michaelrocks.grip.Grip
import io.michaelrocks.grip.mirrors.ClassMirror
import io.michaelrocks.grip.mirrors.MethodMirror
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.getObjectTypeByInternalName
import io.michaelrocks.grip.mirrors.isInterface
import io.michaelrocks.grip.mirrors.isStatic
import io.michaelrocks.grip.mirrors.signature.GenericType
import java.util.HashMap

interface ContractParser {
  fun parseContract(type: Type.Object): Contract
}

class ContractParserImpl(
  private val grip: Grip,
  private val analyzerHelper: AnalyzerHelper,
  private val errorReporter: ErrorReporter,
  private val projectName: String
) : ContractParser {

  private val contractsByTypes = HashMap<Type.Object, Contract>()

  override fun parseContract(type: Type.Object): Contract {
    return contractsByTypes.getOrPut(type) {
      val mirror = grip.classRegistry.getClassMirror(type)
      parseContract(mirror)
    }
  }

  private fun parseContract(mirror: ClassMirror): Contract {
    val implementationType = getObjectTypeByInternalName(mirror.type.internalName + "\$Lightsaber\$Contract\$$projectName")
    val qualifier = analyzerHelper.findQualifier(mirror)
    val dependency = Dependency(GenericType.Raw(mirror.type), qualifier)
    if (!isValidContract(mirror)) {
      return Contract(mirror.type, implementationType, dependency, emptyList())
    }

    val childContracts = mirror.interfaces.map { parseContract(it) }
    val provisionPoints = mirror.methods.mapNotNullTo(ArrayList()) { tryParseContractProvisionPoint(mirror, it) }
    return Contract(mirror.type, implementationType, dependency, mergeContractProvisionPoints(mirror.type, provisionPoints, childContracts))
  }

  private fun isValidContract(mirror: ClassMirror): Boolean {
    if (!mirror.isInterface) {
      errorReporter.reportError("Contract must be an interface: ${mirror.type.className}")
      return false
    }

    if (mirror.signature.typeVariables.isNotEmpty()) {
      errorReporter.reportError("Contract cannot have type parameters: ${mirror.type.className}")
      return false
    }

    return true
  }

  private fun tryParseContractProvisionPoint(mirror: ClassMirror, method: MethodMirror): ContractProvisionPoint? {
    if (method.isStatic) {
      return null
    }

    if (method.signature.typeVariables.isNotEmpty()) {
      errorReporter.reportError("Contract's method  cannot have type parameters: ${mirror.type.className}.${method.name}")
      return null
    }

    if (method.parameters.isNotEmpty()) {
      errorReporter.reportError("Contract's method cannot have parameters: ${mirror.type.className}.${method.name}")
      return null
    }

    val injectee = analyzerHelper.convertMethodResultToInjectee(method)
    return ContractProvisionPoint(mirror.type, method, injectee)
  }

  private fun mergeContractProvisionPoints(
    type: Type.Object,
    provisionPoints: Collection<ContractProvisionPoint>,
    childContracts: Collection<Contract>
  ): Collection<ContractProvisionPoint> {
    val provisionPointsByName = createProvisionPointMap(type, provisionPoints)
    val provisionPointMapForChildContracts = createProvisionPointMapForChildContracts(type, provisionPointsByName.keys, childContracts)
    val allProvisionPointsByName = provisionPointMapForChildContracts + provisionPointsByName
    return allProvisionPointsByName.values
  }

  private fun createProvisionPointMap(type: Type.Object, provisionPoints: Collection<ContractProvisionPoint>): Map<String, ContractProvisionPoint> {
    val provisionPointGroupsByName = provisionPoints.groupBy { it.method.name }
    val provisionPointsByName = linkedMapOf<String, ContractProvisionPoint>()
    provisionPointGroupsByName.forEach { (name, provisionPoints) ->
      if (provisionPoints.size > 1) {
        val provisionPointsString = provisionPoints.joinToString(separator = "\n") { provisionPoint ->
          "  ${provisionPoint.method.signature.returnType} $name()"
        }
        errorReporter.reportError("Interface ${type.className} contains conflicting methods:\n$provisionPointsString")
      }

      provisionPointsByName[name] = provisionPoints.first()
    }

    return provisionPointsByName
  }

  private fun createProvisionPointMapForChildContracts(
    type: Type.Object,
    parentProvisionPointNames: Set<String>,
    childContracts: Collection<Contract>
  ): Map<String, ContractProvisionPoint> {
    val provisionPointGroupsByName =
      childContracts
        .asSequence()
        .flatMap { contract -> contract.provisionPoints.asSequence() }
        .filter { it.method.name !in parentProvisionPointNames }
        .groupBy { it.method.name }
    val provisionPointsByName = linkedMapOf<String, ContractProvisionPoint>()
    provisionPointGroupsByName.forEach { (name, provisionPoints) ->
      if (provisionPoints.size > 1 && !provisionPoints.allEqualBy { it.injectee }) {
        val provisionPointsString = provisionPoints.joinToString(separator = "\n") { provisionPoint ->
          val qualifier = provisionPoint.injectee.dependency.qualifier?.type?.className?.let { "@$it " }
          "  $qualifier${provisionPoint.method.signature.returnType} $name() from ${provisionPoint.container.className}"
        }
        errorReporter.reportError("Interface ${type.className} inherits conflicting methods:\n$provisionPointsString")
      }

      provisionPointsByName[name] = provisionPoints.first()
    }

    return provisionPointsByName
  }

  private inline fun <T, K> Iterable<T>.allEqualBy(selector: (T) -> K): Boolean {
    if (this is Collection && size < 1) {
      return true
    }

    val iterator = iterator()
    if (!iterator.hasNext()) {
      return true
    }

    val element = selector(iterator.next())
    while (iterator.hasNext()) {
      if (element != selector(iterator.next())) {
        return false
      }
    }

    return true
  }
}
