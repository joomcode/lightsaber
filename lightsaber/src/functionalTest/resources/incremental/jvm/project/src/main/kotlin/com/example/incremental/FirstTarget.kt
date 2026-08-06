package com.example.incremental

import com.joom.lightsaber.ProvidedBy
import javax.inject.Inject

@ProvidedBy(TestModule::class)
class FirstTarget @Inject constructor() {
  fun implementationValue(): Int = 1
}
