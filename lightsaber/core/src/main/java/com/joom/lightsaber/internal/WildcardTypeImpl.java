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

package com.joom.lightsaber.internal;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WildcardTypeImpl implements WildcardType {
  @Nonnull
  private final Type[] upperBounds;
  @Nonnull
  private final Type[] lowerBounds;

  public WildcardTypeImpl(
    @Nullable final Type upperBound,
    @Nullable final Type lowerBound
  ) {
    this.upperBounds = upperBound != null ? new Type[]{upperBound} : new Type[0];
    this.lowerBounds = lowerBound != null ? new Type[]{lowerBound} : new Type[0];
  }

  @Override
  public Type[] getUpperBounds() {
    return upperBounds;
  }

  @Override
  public Type[] getLowerBounds() {
    return lowerBounds;
  }

  @Override
  public String getTypeName() {
    if (lowerBounds.length != 0) {
      return "? super " + lowerBounds[0].getTypeName();
    }

    if (upperBounds.length != 0 && !upperBounds[0].equals(Object.class)) {
      return "? extends" + upperBounds[0].getTypeName();
    }

    return "?";
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof WildcardType)) {
      return false;
    }

    if (!Arrays.equals(lowerBounds, ((WildcardType) other).getLowerBounds())) {
      return false;
    }

    return Arrays.equals(upperBounds, ((WildcardType) other).getUpperBounds());
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(upperBounds) ^ Arrays.hashCode(lowerBounds);
  }
}
