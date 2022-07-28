/*
 * Copyright 2022 SIA Joom
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

package test_case_projects.generator.first_project

import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.Factory
import com.joom.lightsaber.Module
import com.joom.lightsaber.Provide
import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.ProvidedBy
import javax.inject.Inject
import javax.inject.Qualifier

interface FirstModuleDependency

@ProvidedBy(FirstDependencyModule::class)
internal class FirstModuleDependencyImpl @Inject constructor() : FirstModuleDependency

@Module
class FirstDependencyModule {

  @Provide
  internal fun provideFirstModuleDependency(firstModuleDependencyImpl: FirstModuleDependencyImpl): FirstModuleDependency {
    return firstModuleDependencyImpl
  }
}

interface FirstContractDependency

interface FirstFactoryCreatedDependency

@ProvidedBy(FirstDependencyContractConfiguration::class)
@ProvidedAs(FirstContractDependency::class)
internal class UnqualifiedFirstContractDependencyImpl @Inject constructor() : FirstContractDependency

@Factory
@ProvidedBy(FirstFactoryCreatedDependencyFactoryModule::class)
interface FirstFactoryCreatedDependencyFactory {
  @Factory.Return(FirstFactoryCreatedDependencyImpl::class)
  fun create(): FirstFactoryCreatedDependency
}

@Module
class FirstFactoryCreatedDependencyFactoryModule

internal class FirstFactoryCreatedDependencyImpl @Factory.Inject constructor() : FirstFactoryCreatedDependency

interface FirstDependencyContract {
  @get:FirstDependencyQualifier
  val dependency: FirstContractDependency
}

class FirstDependencyContractConfiguration : ContractConfiguration<FirstDependencyContract>() {

  @Provide
  @FirstDependencyQualifier
  internal fun provideFirstContractDependency(): FirstContractDependency {
    return object : FirstContractDependency {}
  }
}

@Qualifier
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class FirstDependencyQualifier
