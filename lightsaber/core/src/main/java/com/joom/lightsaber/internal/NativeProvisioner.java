/*
 * Copyright 2026 SIA Joom
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

package com.joom.lightsaber.internal;

import com.joom.lightsaber.Injector;

/**
 * Implemented directly on a module by the Kotlin compiler plugin.
 *
 * A single indexed method replaces the one generated Provider class per
 * provision point used by the bytecode processor. Besides doing less work at
 * compile time this also substantially reduces the number of generated class
 * files passed to D8/R8.
 */
public interface NativeProvisioner {
  Object provide(int id, Injector injector);
}
