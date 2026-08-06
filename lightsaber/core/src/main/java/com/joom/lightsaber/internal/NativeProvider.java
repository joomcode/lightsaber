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

import javax.inject.Provider;

/** Lightweight provider shared by all provision points in a generated module. */
public final class NativeProvider<T> implements Provider<T> {
  private final NativeProvisioner provisioner;
  private final int id;
  private final Injector injector;

  public NativeProvider(NativeProvisioner provisioner, int id, Injector injector) {
    this.provisioner = provisioner;
    this.id = id;
    this.injector = injector;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T get() {
    return (T) provisioner.provide(id, injector);
  }
}
