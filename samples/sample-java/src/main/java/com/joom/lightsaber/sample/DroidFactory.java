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

import com.joom.lightsaber.Factory;
import com.joom.lightsaber.ProvidedBy;

@Factory
@ProvidedBy(LightsaberModule.class)
public interface DroidFactory {
  @Factory.Return(R2D2.class)
  Droid produceR2D2(final String color);
}
