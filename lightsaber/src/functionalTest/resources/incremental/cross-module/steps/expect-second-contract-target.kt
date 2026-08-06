package com.example.impl

import com.joom.lightsaber.Lightsaber
import org.junit.Test

class IncrementalTest {
  @Test
  fun resolvesNewUpstreamContractTarget() {
    Lightsaber.Builder().build().createContract(AppConfiguration()).secondService
  }
}
