package com.example.incremental

import com.joom.lightsaber.ConfigurationException
import com.joom.lightsaber.Lightsaber
import org.junit.Test

class IncrementalTest {
  @Test(expected = ConfigurationException::class)
  fun doesNotResolveRemovedTarget() {
    Lightsaber.Builder().build().createInjector(TestModule()).getInstance(SecondTarget::class.java)
  }
}
