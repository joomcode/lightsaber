/*
 * Copyright 2026 SIA Joom
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

package com.joom.lightsaber.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.load.java.JvmAbi
import java.lang.management.ManagementFactory

/**
 * Native Kotlin implementation of Lightsaber code generation.
 *
 * The bytecode processor emits a class for every Provider. This extension uses
 * one indexed [com.joom.lightsaber.internal.NativeProvisioner] method on each
 * module instead and generates the Kotlin Lightsaber model directly in IR.
 */
internal class LightsaberNativeIrGenerationExtension(
  private val profileLabel: String? = null,
  private val profileReporter: (String) -> Unit = {},
) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val startedAt = System.nanoTime()
    val cpuStartedAt = if (profileLabel != null) threadCpuTime() else 0L
    val scan = moduleFragment.scanLocalClasses()
    val scanFinishedAt = System.nanoTime()
    var phases: Map<String, Long> = emptyMap()
    if (scan.active) {
      phases = NativeGenerator(pluginContext, scan.classes, profileLabel != null).generate()
    }
    profileLabel?.let { label ->
      val durationMs = (System.nanoTime() - startedAt) / 1_000_000.0
      val cpuDurationMs = (threadCpuTime() - cpuStartedAt) / 1_000_000.0
      val phaseSummary = buildString {
        append(" scan=%.3fms".format((scanFinishedAt - startedAt) / 1_000_000.0))
        phases.forEach { (name, duration) ->
          append(" $name=%.3fms".format(duration / 1_000_000.0))
        }
      }
      profileReporter(
        (
          "LIGHTSABER_PROFILE $label %.3fms active=${scan.active} classes=${scan.classes.size} " +
            "cpu=%.3fms$phaseSummary"
          ).format(durationMs, cpuDurationMs)
      )
    }
  }
}

private fun threadCpuTime(): Long {
  return ManagementFactory.getThreadMXBean().currentThreadCpuTime
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class NativeGenerator(
  private val context: IrPluginContext,
  private val localClasses: Set<IrClass>,
  private val profiling: Boolean,
) {
  private val nativeProvisioner by lazySymbol { context.requireClass(NATIVE_PROVISIONER) }
  private val nativeQualifier by lazySymbol { context.requireClass(NATIVE_QUALIFIER) }
  private val key by lazySymbol { context.requireClass(KEY) }
  private val injector by lazySymbol { context.requireClass(INJECTOR) }
  private val lightsaberInjector by lazySymbol { context.requireClass(LIGHTSABER_INJECTOR) }
  private val injectorConfigurator by lazySymbol { context.requireClass(INJECTOR_CONFIGURATOR) }
  private val contractCreator by lazySymbol { context.requireClass(CONTRACT_CREATOR) }
  private val membersInjector by lazySymbol { context.requireClass(MEMBERS_INJECTOR) }
  private val provider by lazySymbol { context.requireClass(PROVIDER) }
  private val lazy by lazySymbol { context.requireClass(LAZY) }
  private val lazyAdapter by lazySymbol { context.requireClass(LAZY_ADAPTER) }
  private val reflectType by lazySymbol { context.requireClass(REFLECT_TYPE) }
  private val parameterizedType by lazySymbol { context.requireClass(PARAMETERIZED_TYPE) }
  private val wildcardType by lazySymbol { context.requireClass(WILDCARD_TYPE) }
  private val illegalArgumentException by lazySymbol { context.requireClass(ILLEGAL_ARGUMENT_EXCEPTION) }
  private val kClassJavaGetter by lazySymbol {
    context.referenceProperties(KCLASS_JAVA_PROPERTY)
      .singleOrNull()?.owner?.getter?.symbol
      ?: error("Lightsaber: cannot resolve kotlin.jvm.KClass.java")
  }
  private val injectMembersFunction by lazySymbol {
    injector.owner.functions.single {
      it.name.asString() == "injectMembers" && it.nonDispatchParameters.size == 1
    }
  }
  private val membersInjectionFunctions by lazySymbol {
    membersInjector.owner.functions.associateBy { it.name.asString() }
  }
  private val nativeProvisionMethod by lazySymbol {
    nativeProvisioner.owner.functions.single { it.name.asString() == "provide" }
  }
  private val configureInjectorMethod by lazySymbol {
    injectorConfigurator.owner.functions.single {
      it.name.asString() == "configureInjector" && it.nonDispatchParameters.size == 1
    }
  }
  private val initializeEagerMethod by lazySymbol {
    injectorConfigurator.owner.functions.single {
      it.name.asString() == "initializeEager" && it.nonDispatchParameters.size == 1
    }
  }
  private val contractCreatorMethod by lazySymbol {
    contractCreator.owner.functions.single { it.name.asString() == "createContract" }
  }
  private val registerNativeProviderMethod by lazySymbol {
    lightsaberInjector.owner.functions.single {
      it.name.asString() == "registerNativeProvider" && it.nonDispatchParameters.size == 4
    }
  }
  private val lazyAdapterConstructor by lazySymbol { lazyAdapter.owner.constructors.single() }
  private val parameterizedTypeConstructor by lazySymbol { parameterizedType.owner.constructors.single() }
  private val wildcardTypeConstructor by lazySymbol { wildcardType.owner.constructors.single() }
  private val keyConstructor by lazySymbol {
    key.owner.constructors.single { it.nonDispatchParameters.size == 2 }
  }
  private val valuedQualifierFactory by lazySymbol {
    nativeQualifier.owner.functions.single {
      it.name.asString() == "of" && it.nonDispatchParameters.size == 3
    }
  }
  private val illegalArgumentExceptionConstructor by lazySymbol {
    illegalArgumentException.owner.constructors.single {
      it.nonDispatchParameters.singleOrNull()?.type?.classOrNull == context.irBuiltIns.stringClass
    }
  }
  private val injectorLookupFunctions = mutableMapOf<Pair<String, FqName>, IrSimpleFunctionSymbol>()
  private val qualifierClasses = mutableMapOf<IrClassSymbol, Boolean>()
  private val contractMethodsByClass = mutableMapOf<IrClassSymbol, List<IrSimpleFunction>>()

  fun generate(): Map<String, Long> {
    val phases = linkedMapOf<String, Long>()
    var phaseStartedAt = if (profiling) System.nanoTime() else 0L
    fun finishPhase(name: String) {
      if (profiling) {
        val finishedAt = System.nanoTime()
        phases[name] = finishedAt - phaseStartedAt
        phaseStartedAt = finishedAt
      }
    }

    val configurations = localClasses.filter(::isConfiguration)
    finishPhase("configurations")

    localClasses.forEach(::addMembersInjection)
    finishPhase("members")

    val entriesByConfiguration = configurations.associateWith { mutableListOf<Provision>() }
    val automaticImportsByConfiguration = configurations.associateWith { mutableListOf<IrClass>() }
    configurations.filter { it.hasAnnotation(MODULE) }.forEach { module ->
      module.importedByClasses().forEach { importer ->
        automaticImportsByConfiguration[importer.owner]?.add(module)
      }
    }
    localClassesInLegacyInjectionOrder().forEach { target ->
      val constructor = target.constructors.singleOrNull { it.hasAnnotation(INJECT) } ?: return@forEach
      target.providedByClasses().forEach { configurationSymbol ->
        val configuration = configurationSymbol.owner
        entriesByConfiguration[configuration]?.let { entries ->
          val qualifier = target.qualifier()
          entries += Provision.Constructor(target, constructor, qualifier)
          target.providedAsClasses().forEach { providedAs ->
            entries += Provision.Alias(target.defaultType, providedAs.defaultType, qualifier)
          }
        }
      }
    }
    finishPhase("constructors")

    configurations.forEach { configuration ->
      configuration.declarations.filterIsInstance<IrSimpleFunction>()
        .filter { it.hasAnnotation(PROVIDE) }
        .forEach { entriesByConfiguration.getValue(configuration) += Provision.Method(it, it.qualifier()) }
      configuration.declarations.filterIsInstance<IrProperty>()
        .filter { it.hasLightsaberAnnotation(PROVIDE) }
        .forEach { property ->
          entriesByConfiguration.getValue(configuration) += Provision.Property(
            property,
            property.qualifier(),
            property.hasLightsaberAnnotation(SINGLETON),
            property.hasLightsaberAnnotation(EAGER),
          )
      }
    }
    finishPhase("provides")

    localClasses.filter { it.hasAnnotation(CONTRACT) }.forEach { contract ->
      contract.providedByClasses().forEach { configurationSymbol ->
        val configuration = configurationSymbol.owner
        entriesByConfiguration[configuration]?.let { entries ->
          val contractType = contract.defaultType
          val implementation = generateContractImplementation(configuration, contractType)
          entries += Provision.Contract(
            contractType,
            implementation.constructors.single(),
            contract.qualifier(),
          )
        }
      }
    }
    finishPhase("providedContracts")

    localClasses.filter { it.hasAnnotation(FACTORY) }.forEach { factory ->
      val implementation = generateFactoryImplementation(factory)
      factory.providedByClasses().forEach { configurationSymbol ->
        entriesByConfiguration[configurationSymbol.owner]?.let { entries ->
          val qualifier = factory.qualifier()
          entries += Provision.Factory(
            factory.defaultType,
            implementation.constructors.single(),
            qualifier,
            factory.hasAnnotation(SINGLETON),
          )
          factory.providedAsClasses().forEach { providedAs ->
            entries += Provision.Alias(factory.defaultType, providedAs.defaultType, qualifier)
          }
        }
      }
    }
    finishPhase("factories")

    var contractsNanos = 0L
    var provisionersNanos = 0L
    var configuratorsNanos = 0L
    val eagerContractClasses = configurations.mapNotNullTo(mutableSetOf()) { configuration ->
      configuration.contractTypeOrNull()?.classOrNull?.takeIf {
        entriesByConfiguration.getValue(configuration).any(Provision::isEager)
      }
    }
    entriesByConfiguration.forEach { (configuration, entries) ->
      var operationStartedAt = if (profiling) System.nanoTime() else 0L
      configuration.importedContractAccessors().forEach { accessor ->
        check(accessor.nonDispatchParameters.isEmpty()) {
          "Lightsaber imported contract accessor ${accessor.name} must not have parameters"
        }
        val importedContractType = accessor.returnType.lazyArgumentOrNull() ?: accessor.returnType
        val importedContract = importedContractType.classOrNull?.owner
          ?: error("Lightsaber imported contract ${accessor.name} must have a class type")
        contractMethods(importedContract).forEach { contractMethod ->
          val returnType = contractMethod.returnType
          val converter = when (returnType.classOrNull) {
            provider -> ContractMethodConverter.PROVIDER
            lazy -> ContractMethodConverter.LAZY
            else -> ContractMethodConverter.INSTANCE
          }
          val providedType = if (converter == ContractMethodConverter.INSTANCE) {
            returnType
          } else {
            (returnType as? IrSimpleType)?.arguments?.singleOrNull()?.typeOrNull
              ?: error("Lightsaber deferred contract dependency must have exactly one concrete type argument: $returnType")
          }
          entries += Provision.ImportedContractMethod(
            accessor,
            contractMethod,
            providedType,
            converter,
            contractMethod.qualifier(),
          )
        }
      }
      configuration.contractTypeOrNull()?.let { contractType ->
        val implementation = generateContractImplementation(configuration, contractType)
        entries += Provision.Contract(
          contractType,
          implementation.constructors.single(),
          contractType.classOrNull?.owner?.qualifier(),
        )
        addContractCreator(configuration, contractType)
      }
      if (profiling) {
        contractsNanos += System.nanoTime() - operationStartedAt
      }
      val automaticImports = automaticImportsByConfiguration.getValue(configuration)
      operationStartedAt = if (profiling) System.nanoTime() else 0L
      if (entries.isNotEmpty()) {
        makeConstructorsCallable(entries)
        addNativeProvisioner(configuration, entries)
      }
      if (profiling) {
        provisionersNanos += System.nanoTime() - operationStartedAt
        operationStartedAt = System.nanoTime()
      }
      addInjectorConfigurator(configuration, entries, automaticImports, eagerContractClasses)
      if (profiling) {
        configuratorsNanos += System.nanoTime() - operationStartedAt
      }
    }
    finishPhase("finalize")
    if (profiling) {
      phases["contracts"] = contractsNanos
      phases["provisioners"] = provisionersNanos
      phases["configurators"] = configuratorsNanos
    }
    return phases
  }

  /**
   * The bytecode processor collected injection targets in a [HashSet] keyed by ASM's object
   * [org.objectweb.asm.Type]. Some applications consequently came to rely on that order for
   * independent eager singletons. Keep the same iteration semantics while moving generation to
   * Kotlin IR; source/file order is different and can reverse observable eager side effects.
   */
  private fun localClassesInLegacyInjectionOrder(): List<IrClass> {
    val keys = HashSet<LegacyObjectTypeKey>()
    localClasses.filter { it.hasInjectionPoint() }.mapTo(keys) { LegacyObjectTypeKey(it) }
    return keys.map { it.target }
  }

  private fun IrClass.hasInjectionPoint(): Boolean =
    constructors.any { it.hasAnnotation(INJECT) } || declarations.any { declaration ->
      when (declaration) {
        is IrProperty -> declaration.hasAnnotation(INJECT) || declaration.hasFirAnnotation(INJECT)
        is IrField -> declaration.hasAnnotation(INJECT) || declaration.hasFirAnnotation(INJECT)
        is IrSimpleFunction -> declaration.hasAnnotation(INJECT)
        else -> false
      }
    }

  private inner class LegacyObjectTypeKey(val target: IrClass) {
    private val internalName = target.jvmInternalName()

    override fun equals(other: Any?): Boolean =
      other is LegacyObjectTypeKey && internalName == other.internalName

    // ASM Type.hashCode() for an object/internal type. Lightsaber's old processor used this hash
    // indirectly through Grip's Type.Object, so matching it also matches HashSet bucket order.
    override fun hashCode(): Int {
      var hash = 13 * 10
      internalName.forEach { character -> hash = 17 * (hash + character.code) }
      return hash
    }
  }

  private fun IrClass.jvmInternalName(): String {
    val enclosingClass = parent as? IrClass
    if (enclosingClass != null) {
      return enclosingClass.jvmInternalName() + "$" + name.asString()
    }
    return checkNotNull(fqNameWhenAvailable) {
      "Lightsaber injectable class must have a qualified name: $name"
    }.asString().replace('.', '/')
  }

  private fun makeConstructorsCallable(entries: List<Provision>) {
    entries.filterIsInstance<Provision.Constructor>().forEach {
      // Old Lightsaber's package invader performs the equivalent access
      // relaxation after compilation. Doing it in IR lets generated code call
      // private Kotlin constructors without another class-file pass.
      it.constructor.visibility = DescriptorVisibilities.INTERNAL
    }
  }

  private fun addMembersInjection(target: IrClass) {
    // Kotlin keeps an annotation without an explicit use-site target on the
    // property in IR and moves it to the backing field later in the JVM
    // pipeline. Inspect both places so native generation sees the same
    // injection points as the old class-file processor.
    val fields = linkedSetOf<IrField>()
    val methods = mutableListOf<IrSimpleFunction>()
    target.declarations.forEach { declaration ->
      when (declaration) {
        is IrProperty -> {
          if (declaration.hasAnnotation(INJECT) || declaration.hasFirAnnotation(INJECT)) {
            declaration.backingField?.let(fields::add)
          }
          declaration.setter?.takeIf {
            it.hasAnnotation(INJECT) || it.hasFirAnnotation(INJECT)
          }?.let(methods::add)
        }
        is IrField -> if (declaration.hasAnnotation(INJECT) || declaration.hasFirAnnotation(INJECT)) {
          fields += declaration
        }
        is IrSimpleFunction -> if (
          (declaration.hasAnnotation(INJECT) || declaration.hasFirAnnotation(INJECT)) &&
          declaration.name.asString() != "<init>"
        ) {
          methods += declaration
        }
      }
    }
    val inheritsMembersInjection = target.superTypes.any { superType ->
      superType.classOrNull?.owner?.superTypes?.any { it.classOrNull == membersInjector } == true
    }
    if (fields.isEmpty() && methods.isEmpty() && !inheritsMembersInjection) return

    fields.forEach { it.isFinal = false }
    target.superTypes += membersInjector.defaultType
    val injectFields = membersInjectionFunctions.getValue("injectFields")
    val injectMethods = membersInjectionFunctions.getValue("injectMethods")
    addMembersInjectionMethod(target, injectFields) { receiver, injectorParameter ->
      fields.forEach { field ->
        +irSetField(
          irGet(receiver),
          field,
          resolveDependency(irGet(injectorParameter), field.type, field.qualifier()),
        )
      }
    }
    addMembersInjectionMethod(target, injectMethods) { receiver, injectorParameter ->
      methods.forEach { method ->
        +irCall(method.symbol).apply {
          dispatchReceiver = irGet(receiver)
          method.nonDispatchParameters.forEach { parameter ->
            arguments[parameter.indexInParameters] = resolveDependency(
              irGet(injectorParameter),
              parameter.type,
              parameter.qualifier(),
            )
          }
        }
      }
    }
  }

  private fun org.jetbrains.kotlin.ir.declarations.IrMetadataSourceOwner.hasFirAnnotation(
    annotationName: FqName,
  ): Boolean {
    val fir = (metadata as? FirMetadataSource)?.fir as? FirAnnotationContainer ?: return false
    val annotations = fir.annotations + ((fir as? FirProperty)?.backingField?.annotations ?: emptyList())
    return annotations.any {
      it.annotationTypeRef.coneType.classId?.asSingleFqName() == annotationName
    }
  }

  private fun addMembersInjectionMethod(
    target: IrClass,
    superMethod: IrSimpleFunction,
    generateBody: org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder.(
      org.jetbrains.kotlin.ir.declarations.IrValueParameter,
      org.jetbrains.kotlin.ir.declarations.IrValueParameter,
    ) -> Unit,
  ) {
    val parentMethod = target.superTypes.asSequence()
      .mapNotNull { it.classOrNull?.owner }
      .filter { it.symbol != membersInjector }
      .mapNotNull { parent ->
        parent.functions.singleOrNull { function ->
          function.name == superMethod.name && function.nonDispatchParameters.size == 1
        }?.let { parent to it }
      }
      .firstOrNull()
    val hasLocalSubclass = localClasses.any { candidate ->
      candidate.superTypes.any { it.classOrNull == target.symbol }
    }
    target.addFunction {
      name = superMethod.name
      returnType = superMethod.returnType
      visibility = DescriptorVisibilities.PUBLIC
      modality = if (hasLocalSubclass) Modality.OPEN else Modality.FINAL
      origin = IrDeclarationOrigin.DEFINED
    }.apply {
      overriddenSymbols = listOfNotNull(parentMethod?.second?.symbol, superMethod.symbol)
      val receiver = target.thisReceiver!!.copyTo(this)
      parameters += receiver
      val injectorParameter = addValueParameter("injector", injector.defaultType)
      body = DeclarationIrBuilder(context, symbol).irBlockBody {
        parentMethod?.let { (parent, method) ->
          +irCall(method.symbol).apply {
            dispatchReceiver = irGet(receiver)
            superQualifierSymbol = parent.symbol
            arguments[method.nonDispatchParameters.single().indexInParameters] = irGet(injectorParameter)
          }
        }
        generateBody(receiver, injectorParameter)
      }
    }
  }

  private fun addNativeProvisioner(configuration: IrClass, entries: List<Provision>) {
    val superMethod = nativeProvisionMethod
    configuration.superTypes += nativeProvisioner.defaultType

    configuration.addFunction {
      name = superMethod.name
      returnType = superMethod.returnType
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.FINAL
      origin = IrDeclarationOrigin.DEFINED
    }.apply {
      overriddenSymbols = listOf(superMethod.symbol)
      val receiver = configuration.thisReceiver!!.copyTo(this)
      parameters += receiver
      val id = addValueParameter("id", context.irBuiltIns.intType)
      val injectorParameter = addValueParameter("injector", injector.defaultType)

      body = DeclarationIrBuilder(context, symbol).irBlockBody {
        val branches = entries.mapIndexed { index, provision ->
          irBranch(
            irEquals(irGet(id), irInt(index)),
            irReturn(generateProvision(provision, injectorParameter, receiver)),
          )
        }
        // This is a statement-level dispatch: every matching branch returns
        // from provide(), while the following throw handles the miss. Using
        // Nothing as the when type makes the JVM backend require an explicit
        // else branch for some platform-flexible provider return types.
        +irWhen(context.irBuiltIns.unitType, branches)
        +irThrow(
          irCallConstructor(illegalArgumentExceptionConstructor.symbol, emptyList()).apply {
            arguments[0] = irString("Unknown Lightsaber provision id")
          }
        )
      }
    }
  }

  private fun org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder.generateProvision(
    provision: Provision,
    injectorParameter: org.jetbrains.kotlin.ir.declarations.IrValueParameter,
    receiver: org.jetbrains.kotlin.ir.declarations.IrValueParameter,
  ): IrExpression = when (provision) {
    is Provision.Constructor -> {
      val instance = irCallConstructor(provision.constructor.symbol, emptyList()).apply {
        provision.constructor.nonDispatchParameters.forEach { parameter ->
          arguments[parameter.indexInParameters] = resolveDependency(
            irGet(injectorParameter),
            parameter.type,
            parameter.qualifier(),
          )
        }
      }
      irBlock(resultType = provision.target.defaultType) {
        val temporary = irTemporary(instance, "instance")
        +irCall(injectMembersFunction.symbol).apply {
          dispatchReceiver = irGet(injectorParameter)
          arguments[injectMembersFunction.nonDispatchParameters.single().indexInParameters] = irGet(temporary)
          }
        +irGet(temporary)
      }
    }

    is Provision.Method -> irCall(provision.function.symbol).apply {
      dispatchReceiver = irGet(receiver)
      provision.function.nonDispatchParameters.forEach { parameter ->
        arguments[parameter.indexInParameters] = resolveDependency(
          irGet(injectorParameter),
          parameter.type,
          parameter.qualifier(),
        )
      }
    }

    is Provision.Property -> {
      val field = provision.property.backingField
      if (field != null) {
        irGetField(irGet(receiver), field)
      } else {
        val getter = provision.property.getter
          ?: error("Lightsaber provided property ${provision.property.name} has neither a field nor a getter")
        irCall(getter.symbol).apply {
          dispatchReceiver = irGet(receiver)
        }
      }
    }

    is Provision.Alias -> resolveDependency(irGet(injectorParameter), provision.sourceType)

    is Provision.Contract -> irCallConstructor(provision.constructor.symbol, emptyList()).apply {
      arguments[provision.constructor.nonDispatchParameters.single().indexInParameters] = irGet(injectorParameter)
    }

    is Provision.ImportedContractMethod -> {
      val importedContractAccessorValue = irCall(provision.contractAccessor.symbol).apply {
        dispatchReceiver = irGet(receiver)
      }
      val importedContract = lazyValue(importedContractAccessorValue, provision.contractAccessor.returnType)
      val importedValue = irCall(provision.contractMethod.symbol).apply {
        dispatchReceiver = importedContract
      }
      when (provision.converter) {
        ContractMethodConverter.INSTANCE -> importedValue
        ContractMethodConverter.PROVIDER -> irCall(
          provider.owner.functions.single {
            it.name.asString() == "get" && it.nonDispatchParameters.isEmpty()
          }.symbol
        ).apply {
          dispatchReceiver = importedValue
        }
        ContractMethodConverter.LAZY -> irCall(
          lazy.owner.functions.single {
            it.name.asString() == "get" && it.nonDispatchParameters.isEmpty()
          }.symbol
        ).apply {
          dispatchReceiver = importedValue
        }
      }
    }

    is Provision.Factory -> irCallConstructor(provision.constructor.symbol, emptyList()).apply {
      arguments[provision.constructor.nonDispatchParameters.single().indexInParameters] = irGet(injectorParameter)
    }
  }

  private fun org.jetbrains.kotlin.ir.builders.IrBuilderWithScope.lazyValue(
    value: IrExpression,
    type: IrType,
  ): IrExpression {
    if (!type.isLazyType()) return value
    val lazyClass = type.classOrNull!!.owner
    val valueAccessor = lazyClass.functions.singleOrNull { function ->
      function.nonDispatchParameters.isEmpty() &&
        (function.name.asString() == "get" || function.correspondingPropertySymbol?.owner?.name?.asString() == "value")
    } ?: lazyClass.declarations.filterIsInstance<IrProperty>()
      .singleOrNull { it.name.asString() == "value" }
      ?.getter
    ?: error("Lightsaber cannot find value accessor on ${lazyClass.fqNameWhenAvailable}")
    return irCall(valueAccessor.symbol).apply {
      dispatchReceiver = value
    }
  }

  private fun org.jetbrains.kotlin.ir.builders.IrBuilderWithScope.resolveDependency(
    injectorExpression: IrExpression,
    requestedType: IrType,
    qualifier: IrConstructorCall? = null,
  ): IrExpression {
    val rawClass = requestedType.classOrNull
      ?: error("Lightsaber native compiler plugin does not support type parameter dependency $requestedType")

    val isProvider = rawClass == provider
    val isLazy = rawClass == lazy
    val dependencyType = if (isProvider || isLazy) {
      ((requestedType as? IrSimpleType)?.arguments?.singleOrNull()?.typeOrNull)
        ?: error("Lightsaber deferred dependency must have exactly one concrete type argument: $requestedType")
    } else {
      requestedType
    }
    check(dependencyType is IrSimpleType && dependencyType.classOrNull != null) {
      "Lightsaber native compiler plugin does not support dependency $dependencyType"
    }

    val methodName = if (isProvider || isLazy) "getProvider" else "getInstance"
    val parameterType = if (qualifier != null) {
      KEY
    } else if (dependencyType.isParameterized()) {
      REFLECT_TYPE
    } else {
      JAVA_CLASS
    }
    val method = injectorLookupFunctions.getOrPut(methodName to parameterType) {
      injector.owner.functions.single {
        it.name.asString() == methodName &&
          it.nonDispatchParameters.singleOrNull()?.type?.classOrNull?.owner?.fqNameWhenAvailable == parameterType
      }.symbol
    }.owner
    val resolved = irCall(method.symbol).apply {
        dispatchReceiver = injectorExpression
        arguments[method.nonDispatchParameters.single().indexInParameters] = if (qualifier == null) {
          typeReference(dependencyType)
        } else {
          keyReference(dependencyType, qualifier)
        }
      }
    val result = if (isLazy) {
      irCallConstructor(lazyAdapterConstructor.symbol, emptyList()).apply {
        arguments[lazyAdapterConstructor.nonDispatchParameters.single().indexInParameters] = resolved
      }
    } else {
      resolved
    }
    return irImplicitCast(
      result,
      requestedType,
    )
  }

  private fun addInjectorConfigurator(
    configuration: IrClass,
    entries: List<Provision>,
    automaticImports: List<IrClass>,
    eagerContractClasses: Set<IrClassSymbol>,
  ) {
    val superMethod = configureInjectorMethod
    configuration.superTypes += injectorConfigurator.defaultType

    configuration.addFunction {
      name = superMethod.name
      returnType = superMethod.returnType
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.FINAL
      origin = IrDeclarationOrigin.DEFINED
    }.apply {
      overriddenSymbols = listOf(superMethod.symbol)
      val receiver = configuration.thisReceiver!!.copyTo(this)
      parameters += receiver
      val injectorParameter = addValueParameter("injector", lightsaberInjector.defaultType)

      body = DeclarationIrBuilder(context, symbol).irBlockBody {
        entries.forEachIndexed { index, provision ->
          +irCall(registerNativeProviderMethod.symbol).apply {
            dispatchReceiver = irGet(injectorParameter)
            arguments[registerNativeProviderMethod.nonDispatchParameters[0].indexInParameters] = if (provision.qualifier == null) {
              typeReference(provision.providedType)
            } else {
              keyReference(provision.providedType, provision.qualifier!!)
            }
            arguments[registerNativeProviderMethod.nonDispatchParameters[1].indexInParameters] = irGet(receiver)
            arguments[registerNativeProviderMethod.nonDispatchParameters[2].indexInParameters] = irInt(index)
            arguments[registerNativeProviderMethod.nonDispatchParameters[3].indexInParameters] = irBoolean(provision.isSingleton)
          }
        }
        configuration.moduleImportAccessors().forEach { (importFunction, companion) ->
            check(importFunction.nonDispatchParameters.isEmpty()) {
              "Lightsaber imported module function ${importFunction.name} must not have parameters"
            }
            val importedModule = irCall(importFunction.symbol).apply {
              dispatchReceiver = companion?.let { irGetObject(it.symbol) } ?: irGet(receiver)
            }
            +irCall(configureInjectorMethod.symbol).apply {
              dispatchReceiver = irImplicitCast(importedModule, injectorConfigurator.defaultType)
              arguments[configureInjectorMethod.nonDispatchParameters.single().indexInParameters] = irGet(injectorParameter)
            }
          }
        automaticImports.forEach { importedModule ->
          val constructor = importedModule.constructors.singleOrNull { it.nonDispatchParameters.isEmpty() }
            ?: error("Lightsaber @ImportedBy module ${importedModule.fqNameWhenAvailable} must have a default constructor")
          constructor.visibility = DescriptorVisibilities.INTERNAL
          val moduleInstance = irCallConstructor(constructor.symbol, emptyList())
          +irCall(configureInjectorMethod.symbol).apply {
            dispatchReceiver = irImplicitCast(moduleInstance, injectorConfigurator.defaultType)
            arguments[configureInjectorMethod.nonDispatchParameters.single().indexInParameters] = irGet(injectorParameter)
          }
        }
      }
    }

    val eagerMethod = initializeEagerMethod
    configuration.addFunction {
      name = eagerMethod.name
      returnType = eagerMethod.returnType
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.FINAL
      origin = IrDeclarationOrigin.DEFINED
    }.apply {
      overriddenSymbols = listOf(eagerMethod.symbol)
      val receiver = configuration.thisReceiver!!.copyTo(this)
      parameters += receiver
      val injectorParameter = addValueParameter("injector", lightsaberInjector.defaultType)
      body = DeclarationIrBuilder(context, symbol).irBlockBody {
        configuration.moduleImportAccessors().forEach { (importFunction, companion) ->
          val importedModule = irCall(importFunction.symbol).apply {
            dispatchReceiver = companion?.let { irGetObject(it.symbol) } ?: irGet(receiver)
          }
          +irCall(eagerMethod.symbol).apply {
            dispatchReceiver = irImplicitCast(importedModule, injectorConfigurator.defaultType)
            arguments[eagerMethod.nonDispatchParameters.single().indexInParameters] = irGet(injectorParameter)
          }
        }
        automaticImports.forEach { importedModule ->
          val constructor = importedModule.constructors.single { it.nonDispatchParameters.isEmpty() }
          +irCall(eagerMethod.symbol).apply {
            dispatchReceiver = irImplicitCast(
              irCallConstructor(constructor.symbol, emptyList()),
              injectorConfigurator.defaultType,
            )
            arguments[eagerMethod.nonDispatchParameters.single().indexInParameters] = irGet(injectorParameter)
          }
        }
        configuration.importedContractAccessors().forEach { accessor ->
          val contractClass = accessor.returnType.lazyArgumentOrNull()?.classOrNull
          if (contractClass in eagerContractClasses) {
            val lazyContract = irCall(accessor.symbol).apply {
              dispatchReceiver = irGet(receiver)
            }
            +lazyValue(lazyContract, accessor.returnType)
          }
        }
        entries.filter { it.isEager }.forEach { provision ->
          +resolveDependency(irGet(injectorParameter), provision.providedType, provision.qualifier)
        }
      }
    }
  }

  private fun generateContractImplementation(configuration: IrClass, contractType: IrType): IrClass {
    val contractClass = contractType.classOrNull?.owner
      ?: error("Lightsaber contract must be a class: $contractType")
    val file = configuration.containingFile()
    val implementation = context.irFactory.buildClass {
      name = Name.identifier(contractClass.jvmInternalName().substringAfterLast('/') + "\$Lightsaber\$Contract")
      kind = ClassKind.CLASS
      visibility = DescriptorVisibilities.INTERNAL
      modality = Modality.FINAL
      origin = IrDeclarationOrigin.DEFINED
    }.apply {
      parent = file
      createThisReceiverParameter()
      superTypes = listOf(contractType)
    }
    file.declarations += implementation

    val injectorField = implementation.addField {
      name = Name.identifier("injector")
      type = injector.defaultType
      visibility = DescriptorVisibilities.PRIVATE
      isFinal = true
      origin = IrDeclarationOrigin.DEFINED
    }
    implementation.addConstructor {
      visibility = DescriptorVisibilities.PUBLIC
      isPrimary = true
      origin = IrDeclarationOrigin.DEFINED
    }.apply {
      val injectorParameter = addValueParameter("injector", injector.defaultType)
      body = DeclarationIrBuilder(context, symbol).irBlockBody {
        +irDelegatingConstructorCall(context.irBuiltIns.anyClass.owner.constructors.single())
        +irSetField(irGet(implementation.thisReceiver!!), injectorField, irGet(injectorParameter))
        +IrInstanceInitializerCallImpl(startOffset, endOffset, implementation.symbol, context.irBuiltIns.unitType)
      }
    }

    val contractMethods = contractMethods(contractClass).map { contractMethod ->
      val property = (contractMethod.parent as? IrClass)?.declarations.orEmpty().filterIsInstance<IrProperty>()
        .firstOrNull { it.getter?.symbol == contractMethod.symbol }
      contractMethod to if (property == null) {
        contractMethod.name
      } else {
        Name.identifier(JvmAbi.getterName(property.name.asString()))
      }
    }
    contractMethods.forEach { (contractMethod, generatedName) ->
      check(contractMethod.nonDispatchParameters.isEmpty()) {
        "Lightsaber contract method ${contractMethod.name} must not have parameters"
      }
      implementation.addFunction {
        name = generatedName
        returnType = contractMethod.returnType
        visibility = DescriptorVisibilities.PUBLIC
        modality = Modality.FINAL
        origin = IrDeclarationOrigin.DEFINED
      }.apply {
        overriddenSymbols = listOf(contractMethod.symbol)
        val receiver = implementation.thisReceiver!!.copyTo(this)
        parameters += receiver
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
          +irReturn(
            resolveDependency(
              irGetField(irGet(receiver), injectorField),
              contractMethod.returnType,
              contractMethod.qualifier(),
            )
          )
        }
      }
    }
    return implementation
  }

  private fun generateFactoryImplementation(factory: IrClass): IrClass {
    check(factory.kind == ClassKind.INTERFACE) { "Lightsaber factory ${factory.fqNameWhenAvailable} must be an interface" }
    val file = factory.containingFile()
    val implementation = context.irFactory.buildClass {
      name = Name.identifier(factory.jvmInternalName().substringAfterLast('/') + "\$Lightsaber\$Factory")
      kind = ClassKind.CLASS
      visibility = DescriptorVisibilities.INTERNAL
      modality = Modality.FINAL
      origin = IrDeclarationOrigin.DEFINED
    }.apply {
      parent = file
      createThisReceiverParameter()
      superTypes = listOf(factory.defaultType)
    }
    file.declarations += implementation

    val injectorField = implementation.addField {
      name = Name.identifier("injector")
      type = injector.defaultType
      visibility = DescriptorVisibilities.PRIVATE
      isFinal = true
      origin = IrDeclarationOrigin.DEFINED
    }
    implementation.addConstructor {
      visibility = DescriptorVisibilities.PUBLIC
      isPrimary = true
      origin = IrDeclarationOrigin.DEFINED
    }.apply {
      val injectorParameter = addValueParameter("injector", injector.defaultType)
      body = DeclarationIrBuilder(context, symbol).irBlockBody {
        +irDelegatingConstructorCall(context.irBuiltIns.anyClass.owner.constructors.single())
        +irSetField(irGet(implementation.thisReceiver!!), injectorField, irGet(injectorParameter))
        +IrInstanceInitializerCallImpl(startOffset, endOffset, implementation.symbol, context.irBuiltIns.unitType)
      }
    }

    factory.functions.filter { it.modality == Modality.ABSTRACT && !it.isFakeOverride }.forEach { factoryMethod ->
      val target = factoryMethod.factoryTarget()
      val constructor = target.owner.constructors.singleOrNull { it.hasAnnotation(FACTORY_INJECT) }
        ?: error("Lightsaber factory target ${target.owner.fqNameWhenAvailable} must have one @Factory.Inject constructor")
      // Dependency symbols are represented by Fir2Ir lazy declarations and
      // must never be mutated. Their Kotlin internal constructors are public
      // in JVM bytecode and can still be called by generated IR.
      if (target.owner in localClasses) {
        constructor.visibility = DescriptorVisibilities.INTERNAL
      }

      implementation.addFunction {
        name = factoryMethod.name
        returnType = factoryMethod.returnType
        visibility = DescriptorVisibilities.PUBLIC
        modality = Modality.FINAL
        origin = IrDeclarationOrigin.DEFINED
      }.apply {
        overriddenSymbols = listOf(factoryMethod.symbol)
        val receiver = implementation.thisReceiver!!.copyTo(this)
        parameters += receiver
        val methodParameters = factoryMethod.nonDispatchParameters.map { it.copyTo(this) }
        parameters += methodParameters
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
          val instance = irCallConstructor(constructor.symbol, emptyList()).apply {
            constructor.nonDispatchParameters.forEach { constructorParameter ->
              arguments[constructorParameter.indexInParameters] = if (constructorParameter.hasAnnotation(FACTORY_PARAMETER)) {
                // Kotlin nullability is not part of the JVM factory method
                // signature. Existing Lightsaber graphs legitimately pass a
                // non-null factory argument into a nullable constructor slot.
                val matching = methodParameters.singleOrNull {
                  sameFactoryParameterType(it.type, constructorParameter.type) &&
                    it.qualifierClass() == constructorParameter.qualifierClass()
                }
                  ?: error(
                    "Lightsaber factory ${factory.fqNameWhenAvailable}.${factoryMethod.name} must have exactly one " +
                      "parameter matching @Factory.Parameter ${constructorParameter.type}"
                  )
                irGet(matching)
              } else {
                resolveDependency(
                  irGetField(irGet(receiver), injectorField),
                  constructorParameter.type,
                  constructorParameter.qualifier(),
                )
              }
            }
          }
          val temporary = irTemporary(instance, "instance")
          +irCall(injectMembersFunction.symbol).apply {
            dispatchReceiver = irGetField(irGet(receiver), injectorField)
            arguments[injectMembersFunction.nonDispatchParameters.single().indexInParameters] = irGet(temporary)
          }
          +irReturn(irGet(temporary))
        }
      }
    }
    return implementation
  }

  private fun sameFactoryParameterType(first: IrType, second: IrType): Boolean {
    val firstSimple = first as? IrSimpleType ?: return first.makeNotNull() == second.makeNotNull()
    val secondSimple = second as? IrSimpleType ?: return false
    if (firstSimple.classifier != secondSimple.classifier) return false
    if (firstSimple.arguments.size != secondSimple.arguments.size) return false
    return firstSimple.arguments.zip(secondSimple.arguments).all { (firstArgument, secondArgument) ->
      val firstType = firstArgument.typeOrNull
      val secondType = secondArgument.typeOrNull
      when {
        firstType == null || secondType == null -> firstType == null && secondType == null
        else -> sameFactoryParameterType(firstType, secondType)
      }
    }
  }

  private fun IrAnnotationContainer.qualifierClass(): IrClassSymbol? {
    return qualifier()?.type?.classOrNull
  }

  private fun IrAnnotationContainer.qualifier(): IrConstructorCall? {
    val qualifiers = annotations.filter { annotation ->
      val qualifierClass = annotation.type.classOrNull ?: return@filter false
      qualifierClasses.getOrPut(qualifierClass) { qualifierClass.owner.hasAnnotation(QUALIFIER) }
    }
    check(qualifiers.size <= 1) { "Lightsaber element has multiple qualifiers: $qualifiers" }
    return qualifiers.singleOrNull()
  }

  private fun IrSimpleFunction.factoryTarget(): IrClassSymbol {
    val annotation = getAnnotation(FACTORY_RETURN)
    if (annotation != null) {
      return annotation.arguments.flatMap(::classReferences).singleOrNull()
        ?: error("Lightsaber @Factory.Return on $name must contain one class")
    }
    return returnType.classOrNull ?: error("Lightsaber factory method $name must return a class")
  }

  private fun addContractCreator(configuration: IrClass, contractType: IrType) {
    val superMethod = contractCreatorMethod
    configuration.superTypes += contractCreator.typeWith(contractType)
    configuration.addFunction {
      name = superMethod.name
      returnType = context.irBuiltIns.anyNType
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.FINAL
      origin = IrDeclarationOrigin.DEFINED
    }.apply {
      overriddenSymbols = listOf(superMethod.symbol)
      val receiver = configuration.thisReceiver!!.copyTo(this)
      parameters += receiver
      val injectorParameter = addValueParameter("injector", injector.defaultType)
      body = DeclarationIrBuilder(context, symbol).irBlockBody {
        +irReturn(resolveDependency(irGet(injectorParameter), contractType))
      }
    }
  }

  private fun org.jetbrains.kotlin.ir.builders.IrBuilderWithScope.javaClassReference(type: IrType): IrExpression {
    val kClassReference = IrClassReferenceImpl(
      startOffset,
      endOffset,
      context.irBuiltIns.kClassClass.starProjectedType,
      type.classOrNull ?: error("Cannot create a class reference for $type"),
      type.makeNotNull(),
    )
    return irCall(kClassJavaGetter).apply {
      arguments[kClassJavaGetter.owner.nonDispatchParameters.single().indexInParameters] = kClassReference
    }
  }

  private fun org.jetbrains.kotlin.ir.builders.IrBuilderWithScope.typeReference(type: IrType): IrExpression {
    val simpleType = type as? IrSimpleType
      ?: error("Lightsaber cannot create a runtime Type for $type")
    if (!simpleType.isParameterized()) {
      return javaClassReference(type)
    }

    return irCallConstructor(parameterizedTypeConstructor.symbol, emptyList()).apply {
      arguments[parameterizedTypeConstructor.nonDispatchParameters[0].indexInParameters] = irNull(reflectType.defaultType)
      arguments[parameterizedTypeConstructor.nonDispatchParameters[1].indexInParameters] = javaClassReference(simpleType.classOrNull!!.owner.defaultType)
      arguments[parameterizedTypeConstructor.nonDispatchParameters[2].indexInParameters] = irVararg(
        reflectType.defaultType,
        simpleType.arguments.map { argument ->
          argument.typeOrNull?.let { typeReference(it) } ?: starProjectionReference()
        },
      )
    }
  }

  private fun org.jetbrains.kotlin.ir.builders.IrBuilderWithScope.starProjectionReference(): IrExpression {
    return irCallConstructor(wildcardTypeConstructor.symbol, emptyList()).apply {
      arguments[wildcardTypeConstructor.nonDispatchParameters[0].indexInParameters] =
        javaClassReference(context.irBuiltIns.anyType)
      arguments[wildcardTypeConstructor.nonDispatchParameters[1].indexInParameters] = irNull(reflectType.defaultType)
    }
  }

  private fun org.jetbrains.kotlin.ir.builders.IrBuilderWithScope.keyReference(
    type: IrType,
    qualifier: IrConstructorCall,
  ): IrExpression {
    val qualifierType = qualifier.type.classOrNull?.owner
      ?: error("Lightsaber qualifier must have a class type: ${qualifier.type}")
    val explicitArguments = qualifier.symbol.owner.nonDispatchParameters.mapNotNull { parameter ->
      qualifier.arguments[parameter.indexInParameters]?.let { parameter.name.asString() to it }
    }
    val qualifierInstance = irCall(valuedQualifierFactory.symbol).apply {
      arguments[valuedQualifierFactory.nonDispatchParameters[0].indexInParameters] = javaClassReference(qualifier.type)
      arguments[valuedQualifierFactory.nonDispatchParameters[1].indexInParameters] = irVararg(
        context.irBuiltIns.stringType,
        explicitArguments.map { (name, _) -> irString(name) },
      )
      arguments[valuedQualifierFactory.nonDispatchParameters[2].indexInParameters] = irVararg(
        context.irBuiltIns.anyNType,
        explicitArguments.map { (_, value) ->
          irImplicitCast(annotationValueReference(value), context.irBuiltIns.anyNType)
        },
      )
    }
    return irCallConstructor(keyConstructor.symbol, emptyList()).apply {
      arguments[keyConstructor.nonDispatchParameters[0].indexInParameters] = typeReference(type)
      arguments[keyConstructor.nonDispatchParameters[1].indexInParameters] = qualifierInstance
    }
  }

  private fun org.jetbrains.kotlin.ir.builders.IrBuilderWithScope.annotationValueReference(
    value: IrExpression,
  ): IrExpression {
    if (value is IrClassReference) {
      return javaClassReference(value.classType)
    }
    if (value is IrVararg && value.elements.all { it is IrClassReference }) {
      return irVararg(
        this@NativeGenerator.context.requireClass(JAVA_CLASS).defaultType,
        value.elements.map { javaClassReference((it as IrClassReference).classType) },
      )
    }
    return value.deepCopyWithSymbols()
  }

  private fun IrType.isParameterized(): Boolean {
    return (this as? IrSimpleType)?.arguments?.isNotEmpty() == true
  }

  private fun IrType.isLazyType(): Boolean {
    return classOrNull?.owner?.fqNameWhenAvailable == LAZY ||
      classOrNull?.owner?.fqNameWhenAvailable == KOTLIN_LAZY
  }

  private fun IrType.lazyArgumentOrNull(): IrType? {
    if (!isLazyType()) return null
    return (this as? IrSimpleType)?.arguments?.singleOrNull()?.typeOrNull
      ?: error("Lightsaber lazy contract must have exactly one concrete type argument: $this")
  }

  private fun isConfiguration(irClass: IrClass): Boolean {
    return irClass.hasAnnotation(MODULE) || irClass.hasAnnotation(COMPONENT) || irClass.isContractConfiguration()
  }

  private fun IrClass.isContractConfiguration(): Boolean {
    return contractTypeOrNull() != null
  }

  private fun IrClass.contractTypeOrNull(): IrType? {
    val superType = superTypes.singleOrNull {
      it.classOrNull?.owner?.fqNameWhenAvailable == CONTRACT_CONFIGURATION
    } as? IrSimpleType ?: return null
    return superType.arguments.singleOrNull()?.typeOrNull
  }

  private fun IrClass.providedByClasses(): List<IrClassSymbol> {
    val annotation = getAnnotation(PROVIDED_BY) ?: return emptyList()
    return annotation.arguments.flatMap(::classReferences)
  }

  private fun IrClass.providedAsClasses(): List<IrClassSymbol> {
    val annotation = getAnnotation(PROVIDED_AS) ?: return emptyList()
    return annotation.arguments.flatMap(::classReferences)
  }

  private fun IrClass.importedByClasses(): List<IrClassSymbol> {
    val annotation = getAnnotation(IMPORTED_BY) ?: return emptyList()
    return annotation.arguments.flatMap(::classReferences)
  }

  private fun IrClass.importedContractAccessors(): List<IrSimpleFunction> {
    val propertyGetters = declarations.filterIsInstance<IrProperty>().mapNotNull { property ->
      property.getter?.takeIf {
        property.hasLightsaberAnnotation(IMPORT) && property.hasLightsaberAnnotation(CONTRACT)
      }
    }
    val functions = declarations.filterIsInstance<IrSimpleFunction>().filter { function ->
      function.hasAnnotation(IMPORT) && function.hasAnnotation(CONTRACT)
    }
    return propertyGetters + functions
  }

  private fun IrClass.moduleImportAccessors(): List<Pair<IrSimpleFunction, IrClass?>> {
    fun IrClass.directAccessors(): List<IrSimpleFunction> {
      val propertyGetters = declarations.filterIsInstance<IrProperty>().mapNotNull { property ->
        property.getter?.takeIf {
          property.hasLightsaberAnnotation(IMPORT) && !property.hasLightsaberAnnotation(CONTRACT)
        }
      }
      val functions = declarations.filterIsInstance<IrSimpleFunction>().filter { function ->
        function.hasAnnotation(IMPORT) && !function.hasAnnotation(CONTRACT)
      }
      return propertyGetters + functions
    }

    return buildList {
      directAccessors().forEach { add(it to null) }
      declarations.filterIsInstance<IrClass>()
        .filter { it.kind == ClassKind.OBJECT && it.name.asString() == "Companion" }
        .forEach { companion -> companion.directAccessors().forEach { add(it to companion) } }
    }
  }

  private fun IrProperty.hasLightsaberAnnotation(annotation: FqName): Boolean {
    return hasAnnotation(annotation) ||
      backingField?.hasAnnotation(annotation) == true ||
      getter?.hasAnnotation(annotation) == true
  }

  private fun IrProperty.qualifier(): IrConstructorCall? {
    val qualifiers = buildList {
      (this@qualifier as IrAnnotationContainer).qualifier()?.let(::add)
      backingField?.qualifier()?.let(::add)
      getter?.qualifier()?.let(::add)
    }.distinctBy { it.type.classOrNull }
    check(qualifiers.size <= 1) { "Lightsaber property $name has multiple qualifiers: $qualifiers" }
    return qualifiers.singleOrNull()
  }

  private fun contractMethods(contractClass: IrClass): List<IrSimpleFunction> {
    return contractMethodsByClass.getOrPut(contractClass.symbol) {
      collectContractMethods(contractClass)
    }
  }

  private fun collectContractMethods(contractClass: IrClass): List<IrSimpleFunction> {
    val classes = buildList {
      fun collect(current: IrClass) {
        add(current)
        current.superTypes.mapNotNull { it.classOrNull?.owner }
          .filter { it != context.irBuiltIns.anyClass.owner }
          .forEach(::collect)
      }
      collect(contractClass)
    }
    return classes.flatMap { current ->
      buildList {
        current.declarations.filterIsInstance<IrSimpleFunction>()
          .filter { it.modality == Modality.ABSTRACT && !it.isFakeOverride }
          .forEach(::add)
        current.declarations.filterIsInstance<IrProperty>().forEach { property ->
          property.getter?.takeIf { it.modality == Modality.ABSTRACT && !it.isFakeOverride }?.let(::add)
        }
      }
    }.distinctBy { method ->
      method.name.asString() to method.nonDispatchParameters.map { it.type.toString() }
    }
  }

  private fun classReferences(expression: IrExpression?): List<IrClassSymbol> = when (expression) {
    is IrClassReference -> listOfNotNull(expression.symbol as? IrClassSymbol)
    is IrVararg -> expression.elements.flatMap { classReferences(it as? IrExpression) }
    else -> emptyList()
  }

  private fun IrDeclaration.containingFile(): IrFile {
    var current: Any = parent
    while (current !is IrFile) {
      current = (current as IrDeclaration).parent
    }
    return current
  }

  private sealed class Provision {
    abstract val providedType: IrType
    abstract val qualifier: IrConstructorCall?
    abstract val isSingleton: Boolean
    abstract val isEager: Boolean

    class Constructor(
      val target: IrClass,
      val constructor: IrConstructor,
      override val qualifier: IrConstructorCall?,
    ) : Provision() {
      override val providedType: IrType get() = target.defaultType
      override val isSingleton: Boolean get() = target.hasAnnotation(SINGLETON)
      override val isEager: Boolean get() = target.hasAnnotation(EAGER)
    }

    class Method(
      val function: IrSimpleFunction,
      override val qualifier: IrConstructorCall?,
    ) : Provision() {
      override val providedType: IrType get() = function.returnType
      override val isSingleton: Boolean get() = function.hasAnnotation(SINGLETON)
      override val isEager: Boolean get() = function.hasAnnotation(EAGER)
    }

    class Property(
      val property: IrProperty,
      override val qualifier: IrConstructorCall?,
      override val isSingleton: Boolean,
      override val isEager: Boolean,
    ) : Provision() {
      override val providedType: IrType
        get() = property.backingField?.type
          ?: property.getter?.returnType
          ?: error("Lightsaber provided property ${property.name} has no type")
    }

    class Alias(
      val sourceType: IrType,
      override val providedType: IrType,
      override val qualifier: IrConstructorCall?,
    ) : Provision() {
      override val isSingleton: Boolean get() = false
      override val isEager: Boolean get() = false
    }

    class Contract(
      override val providedType: IrType,
      val constructor: IrConstructor,
      override val qualifier: IrConstructorCall?,
    ) : Provision() {
      override val isSingleton: Boolean get() = false
      override val isEager: Boolean get() = false
    }

    class ImportedContractMethod(
      val contractAccessor: IrSimpleFunction,
      val contractMethod: IrSimpleFunction,
      override val providedType: IrType,
      val converter: ContractMethodConverter,
      override val qualifier: IrConstructorCall?,
    ) : Provision() {
      override val isSingleton: Boolean get() = false
      override val isEager: Boolean get() = false
    }

    class Factory(
      override val providedType: IrType,
      val constructor: IrConstructor,
      override val qualifier: IrConstructorCall?,
      override val isSingleton: Boolean,
    ) : Provision() {
      override val isEager: Boolean get() = false
    }
  }

  private enum class ContractMethodConverter {
    INSTANCE,
    PROVIDER,
    LAZY,
  }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private data class LocalClassScan(
  val classes: Set<IrClass>,
  val active: Boolean,
)

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrModuleFragment.scanLocalClasses(): LocalClassScan {
  val classes = linkedSetOf<IrClass>()
  var active = false

  fun visit(declaration: IrDeclaration) {
    if (!active && declaration.hasLightsaberWorkAnnotation()) {
      active = true
    }
    if (declaration is IrClass) {
      classes += declaration
      if (!active && declaration.superTypes.any {
          it.classOrNull?.owner?.fqNameWhenAvailable == CONTRACT_CONFIGURATION
        }) {
        active = true
      }
      declaration.declarations.forEach(::visit)
    }
  }
  files.forEach { file -> file.declarations.forEach(::visit) }
  return LocalClassScan(classes, active)
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrDeclaration.hasLightsaberWorkAnnotation(): Boolean {
  return annotations.any { annotation ->
    annotation.type.classOrNull?.owner?.fqNameWhenAvailable in LIGHTSABER_WORK_ANNOTATIONS
  } || (this as? org.jetbrains.kotlin.ir.declarations.IrMetadataSourceOwner)
    ?.hasFirWorkAnnotation() == true
}

private fun org.jetbrains.kotlin.ir.declarations.IrMetadataSourceOwner.hasFirWorkAnnotation(): Boolean {
  val fir = (metadata as? FirMetadataSource)?.fir as? FirAnnotationContainer ?: return false
  val annotations = fir.annotations + ((fir as? FirProperty)?.backingField?.annotations ?: emptyList())
  return annotations.any {
    it.annotationTypeRef.coneType.classId?.asSingleFqName() in LIGHTSABER_WORK_ANNOTATIONS
  }
}

private fun IrPluginContext.requireClass(fqName: FqName): IrClassSymbol {
  return referenceClass(ClassId.topLevel(fqName))
    ?: error("Lightsaber: required class $fqName is missing from the compilation classpath")
}

private fun <T> lazySymbol(initializer: () -> T): Lazy<T> {
  return lazy(LazyThreadSafetyMode.NONE, initializer)
}

private val NATIVE_PROVISIONER = FqName("com.joom.lightsaber.internal.NativeProvisioner")
private val NATIVE_QUALIFIER = FqName("com.joom.lightsaber.internal.NativeQualifier")
private val KEY = FqName("com.joom.lightsaber.Key")
private val INJECTOR = FqName("com.joom.lightsaber.Injector")
private val LIGHTSABER_INJECTOR = FqName("com.joom.lightsaber.internal.LightsaberInjector")
private val INJECTOR_CONFIGURATOR = FqName("com.joom.lightsaber.internal.InjectorConfigurator")
private val CONTRACT_CREATOR = FqName("com.joom.lightsaber.internal.ContractCreator")
private val MEMBERS_INJECTOR = FqName("com.joom.lightsaber.internal.MembersInjector")
private val PROVIDER = FqName("javax.inject.Provider")
private val LAZY = FqName("com.joom.lightsaber.Lazy")
private val KOTLIN_LAZY = FqName("kotlin.Lazy")
private val LAZY_ADAPTER = FqName("com.joom.lightsaber.LazyAdapter")
private val JAVA_CLASS = FqName("java.lang.Class")
private val REFLECT_TYPE = FqName("java.lang.reflect.Type")
private val PARAMETERIZED_TYPE = FqName("com.joom.lightsaber.internal.ParameterizedTypeImpl")
private val WILDCARD_TYPE = FqName("com.joom.lightsaber.internal.WildcardTypeImpl")
private val ILLEGAL_ARGUMENT_EXCEPTION = FqName("java.lang.IllegalArgumentException")
private val CONTRACT_CONFIGURATION = FqName("com.joom.lightsaber.ContractConfiguration")
private val MODULE = FqName("com.joom.lightsaber.Module")
private val COMPONENT = FqName("com.joom.lightsaber.Component")
private val PROVIDED_BY = FqName("com.joom.lightsaber.ProvidedBy")
private val PROVIDED_AS = FqName("com.joom.lightsaber.ProvidedAs")
private val IMPORTED_BY = FqName("com.joom.lightsaber.ImportedBy")
private val PROVIDE = FqName("com.joom.lightsaber.Provide")
private val IMPORT = FqName("com.joom.lightsaber.Import")
private val CONTRACT = FqName("com.joom.lightsaber.Contract")
private val FACTORY = FqName("com.joom.lightsaber.Factory")
private val FACTORY_INJECT = FqName("com.joom.lightsaber.Factory.Inject")
private val FACTORY_PARAMETER = FqName("com.joom.lightsaber.Factory.Parameter")
private val FACTORY_RETURN = FqName("com.joom.lightsaber.Factory.Return")
private val INJECT = FqName("javax.inject.Inject")
private val SINGLETON = FqName("javax.inject.Singleton")
private val EAGER = FqName("com.joom.lightsaber.Eager")
private val QUALIFIER = FqName("javax.inject.Qualifier")
private val LIGHTSABER_WORK_ANNOTATIONS = setOf(
  MODULE,
  COMPONENT,
  PROVIDED_BY,
  PROVIDED_AS,
  IMPORTED_BY,
  PROVIDE,
  IMPORT,
  CONTRACT,
  FACTORY,
  FACTORY_INJECT,
  FACTORY_PARAMETER,
  FACTORY_RETURN,
  INJECT,
  SINGLETON,
  EAGER,
)
private val KCLASS_JAVA_PROPERTY = org.jetbrains.kotlin.name.CallableId(FqName("kotlin.jvm"), Name.identifier("java"))
