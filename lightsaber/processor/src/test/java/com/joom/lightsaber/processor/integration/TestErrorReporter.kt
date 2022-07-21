package com.joom.lightsaber.processor.integration

import com.joom.lightsaber.processor.ErrorReporter
import org.junit.Assert

class TestErrorReporter : ErrorReporter {
  private val errors = ArrayList<LoggedError>()

  override fun reportError(errorMessage: String, exception: Throwable?) {
    errors += LoggedError(errorMessage, exception)
  }

  override val hasErrors: Boolean
    get() = errors.isNotEmpty()

  fun assertErrorReported(message: String) {
    Assert.assertTrue("Expected '${message}', got:\n${errorsToString()}", errors.any { it.message == message })
  }

  fun assertNoErrorsReported() {
    Assert.assertFalse("Expected no errors, got\n${errorsToString()}", hasErrors)
  }

  private fun errorsToString(): String {
    return errors.joinToString("\n") { it.message }
  }

  private data class LoggedError(
    val message: String,
    val throwable: Throwable?
  )
}