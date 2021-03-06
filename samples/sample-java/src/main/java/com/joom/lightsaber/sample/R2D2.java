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

import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

public class R2D2 implements Droid {
  private final Body body;
  private final String color;

  @Factory.Inject
  public R2D2(final Body body, @Factory.Parameter final String color) {
    this.body = body;
    this.color = color;
  }

  @Override
  public void repair() {
    System.out.println("BEEP BEEP BEEEEEP");
  }

  @Override
  public String toString() {
    return "R2-D2, S/N: " + body.serialNumber + ", color: " + color;
  }

  @ProvidedBy(LightsaberModule.class)
  public static class Body {
    private static final AtomicInteger serialNumberCounter = new AtomicInteger();

    private final int serialNumber = serialNumberCounter.incrementAndGet();

    @Inject
    public Body() {
    }
  }
}
