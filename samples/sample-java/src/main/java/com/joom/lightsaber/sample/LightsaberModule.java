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

package com.joom.lightsaber.sample;

import javax.inject.Provider;
import javax.inject.Singleton;

import com.joom.lightsaber.ImportedBy;
import com.joom.lightsaber.Module;
import com.joom.lightsaber.Provide;

@Module(isDefault = true)
@ImportedBy(LightsaberComponent.class)
class LightsaberModule {
  @Provide
  private Droid provideDroid(final DroidFactory factory) {
    return factory.produceR2D2("Silver");
  }

  @Provide
  @Singleton
  private Planet providePlanet(final Provider<Kashyyyk> kashyyykProvider) {
    return kashyyykProvider.get();
  }
}