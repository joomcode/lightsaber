package com.example.incremental

import com.joom.lightsaber.ProvidedBy
import javax.inject.Inject

@ProvidedBy(TestModule::class)
class FirstTarget @Inject constructor() {
  // An implementation-only edit must not invalidate the aggregated DI graph.
  fun implementationValue(): Int = 2
}
