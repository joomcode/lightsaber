package com.example.impl

import com.joom.lightsaber.Lightsaber
import org.junit.Test

class IncrementalTest {
  @Test
  fun resolvesFirstTarget() {
    Lightsaber.Builder().build().createContract(AppConfiguration()).firstService
  }
}
