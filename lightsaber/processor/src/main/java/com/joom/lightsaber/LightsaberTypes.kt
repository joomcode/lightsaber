/*
 * Copyright 2020 SIA Joom
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

package com.joom.lightsaber

import com.joom.grip.mirrors.getObjectType
import com.joom.lightsaber.internal.ContractCreator
import com.joom.lightsaber.internal.InjectorConfigurator
import com.joom.lightsaber.internal.LightsaberInjector
import com.joom.lightsaber.internal.MembersInjector
import com.joom.lightsaber.internal.SingletonProvider

object LightsaberTypes {
  val INJECTOR_CONFIGURATOR_TYPE = getObjectType<InjectorConfigurator>()
  val MEMBERS_INJECTOR_TYPE = getObjectType<MembersInjector>()
  val LIGHTSABER_INJECTOR_TYPE = getObjectType<LightsaberInjector>()
  val SINGLETON_PROVIDER_TYPE = getObjectType<SingletonProvider<*>>()
  val LAZY_ADAPTER_TYPE = getObjectType<LazyAdapter<*>>()
  val CONTRACT_CREATOR_TYPE = getObjectType<ContractCreator<*>>()
}
