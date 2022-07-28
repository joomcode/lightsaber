package com.joom.lightsaber.modular

import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.Provide
import javax.inject.Qualifier

@Qualifier
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ContractQualifier

interface LibraryContract {
  @get:ContractQualifier
  val qualifiedContractDependency: ModuleDependency
}

class LibraryContractConfiguration : ContractConfiguration<LibraryContract>() {
  @Provide
  @ContractQualifier
  fun provideQualifiedDependency(): ModuleDependency {
    return object : ModuleDependency {
      override fun printInfo() {
        println("QualifiedContractDependencyImpl")
      }
    }
  }
}
