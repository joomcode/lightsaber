package com.joom.lightsaber.nativeirsmoke

import com.joom.lightsaber.Eager
import com.joom.lightsaber.ProvidedBy
import javax.inject.Inject
import javax.inject.Singleton

internal object EagerInstantiationOrder {
  val values = mutableListOf<String>()
}

// Source order intentionally differs from the legacy processor's HashSet iteration order.
@Eager
@Singleton
@ProvidedBy(NativeModule::class)
internal class SourceFirstEagerDependency @Inject private constructor() {
  init {
    EagerInstantiationOrder.values += "source-first"
  }
}

@Eager
@Singleton
@ProvidedBy(NativeModule::class)
internal class SourceSecondEagerDependency @Inject private constructor() {
  init {
    EagerInstantiationOrder.values += "source-second"
  }
}
