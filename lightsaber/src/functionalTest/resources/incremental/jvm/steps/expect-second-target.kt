package com.example.incremental

import com.joom.lightsaber.Lightsaber
import org.junit.Test

class IncrementalTest {
  @Test
  fun resolvesTarget() {
    Lightsaber.Builder().build().createInjector(TestModule()).getInstance(SecondTarget::class.java)
  }
}
