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

package com.joom.lightsaber;

import com.joom.lightsaber.internal.ContractCreator;
import com.joom.lightsaber.internal.LightsaberInjector;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Provider;

public class Lightsaber {
  private final List<ProviderInterceptor> interceptors;

  Lightsaber(final Builder builder) {
    interceptors = builder.interceptors == null ? null : new ArrayList<ProviderInterceptor>(builder.interceptors);
  }

  @Nonnull
  public Builder newBuilder() {
    return new Builder(this);
  }

  @Nonnull
  public Injector createInjector(@Nonnull final Object component) {
    return new LightsaberInjector(component, null, interceptors);
  }

  @Nonnull
  public <T> T createContract(@Nonnull final ContractConfiguration<T> contractConfiguration) {
    @SuppressWarnings("unchecked")
    final ContractCreator<T> creator = (ContractCreator<T>) contractConfiguration;
    return creator.createContract(createInjector(contractConfiguration));
  }

  @Nonnull
  public static <T> T getInstance(@Nonnull final Injector injector, @Nonnull final Class<? extends T> type) {
    return injector.getInstance(type);
  }

  @Nonnull
  public static <T> T getInstance(@Nonnull final Injector injector, @Nonnull final Class<? extends T> type,
      @Nullable final Annotation annotation) {
    return injector.getInstance(Key.of(type, annotation));
  }

  @Nonnull
  public static <T> Provider<T> getProvider(@Nonnull final Injector injector, @Nonnull final Class<? extends T> type) {
    return injector.getProvider(type);
  }

  @Nonnull
  public static <T> Provider<T> getProvider(@Nonnull final Injector injector, @Nonnull final Class<? extends T> type,
      @Nullable final Annotation annotation) {
    return injector.getProvider(Key.of(type, annotation));
  }

  public static class Builder {
    private List<ProviderInterceptor> interceptors;

    public Builder() {
    }

    Builder(@Nonnull final Lightsaber lightsaber) {
      interceptors = lightsaber.interceptors == null ? null : new ArrayList<ProviderInterceptor>(lightsaber.interceptors);
    }

    /**
     * Adds a {@link ProviderInterceptor} to the interceptor chain. Added interceptors will be invoked in the reverse order.
     * <p>
     * <strong>WARNING!</strong> Provider interception affects performance negatively. If a single interceptor is added each dependency resolution
     * produces at least two additional allocations even if it's not affected by the interceptor.
     * </p>
     *
     * @param interceptor
     *     The {@link ProviderInterceptor} to add to the interceptor chain.
     * @return The current {@link Builder} instance.
     */
    @Nonnull
    public Builder addProviderInterceptor(@Nonnull final ProviderInterceptor interceptor) {
      // noinspection ConstantConditions
      if (interceptor == null) {
        throw new NullPointerException("Interceptor is null");
      }

      if (interceptors == null) {
        interceptors = new ArrayList<ProviderInterceptor>();
      }

      interceptors.add(interceptor);
      return this;
    }

    @Nonnull
    public Lightsaber build() {
      return new Lightsaber(this);
    }
  }

  private static final class Holder {
    static final Lightsaber INSTANCE = new Lightsaber.Builder().build();
  }
}
