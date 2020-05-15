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

import com.joom.lightsaber.internal.InjectorConfigurator;
import com.joom.lightsaber.internal.LightsaberInjector;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.annotation.Nonnull;
import javax.inject.Named;
import javax.inject.Provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class LightsaberTest {
  @Test
  public void testCreateInjector() {
    final com.joom.lightsaber.Lightsaber lightsaber = new com.joom.lightsaber.Lightsaber.Builder().build();
    final InjectorConfigurator parentComponent = createParentComponent();

    final com.joom.lightsaber.Injector injector = lightsaber.createInjector(parentComponent);

    verify(parentComponent).configureInjector((LightsaberInjector) injector);
    verifyNoMoreInteractions(parentComponent);
    assertSame(injector, injector.getInstance(com.joom.lightsaber.Key.of(com.joom.lightsaber.Injector.class)));
    assertEquals("Parent String", injector.getInstance(String.class));
    assertEquals("Parent String", injector.getInstance(com.joom.lightsaber.Key.of(String.class)));
  }

  @Test
  public void testCreateChildInjector() {
    final com.joom.lightsaber.Lightsaber lightsaber = new com.joom.lightsaber.Lightsaber.Builder().build();
    final InjectorConfigurator parentComponent = createParentComponent();
    final InjectorConfigurator childComponent = createChildComponent();

    final com.joom.lightsaber.Injector injector = lightsaber.createInjector(parentComponent);
    final com.joom.lightsaber.Injector childInjector = injector.createChildInjector(childComponent);

    verify(parentComponent).configureInjector((LightsaberInjector) injector);
    verifyNoMoreInteractions(parentComponent);
    verify(childComponent).configureInjector((LightsaberInjector) childInjector);
    verifyNoMoreInteractions(childComponent);
    assertSame(injector, injector.getInstance(com.joom.lightsaber.Key.of(com.joom.lightsaber.Injector.class)));
    assertSame(childInjector, childInjector.getInstance(com.joom.lightsaber.Key.of(com.joom.lightsaber.Injector.class)));
    assertEquals("Parent String", childInjector.getInstance(String.class));
    assertEquals("Parent String", childInjector.getInstance(com.joom.lightsaber.Key.of(String.class)));
    assertEquals("Child Object", childInjector.getInstance(Object.class));
    assertEquals("Child Object", childInjector.getInstance(com.joom.lightsaber.Key.of(Object.class)));
  }

  @Test
  public void testCreateChildInjectorWithAnnotation() {
    final com.joom.lightsaber.Lightsaber lightsaber = new com.joom.lightsaber.Lightsaber.Builder().build();
    final InjectorConfigurator parentComponent = createParentComponent();
    final InjectorConfigurator childAnnotatedComponent = createChildAnnotatedComponent();

    final com.joom.lightsaber.Injector injector = lightsaber.createInjector(parentComponent);
    final com.joom.lightsaber.Injector childInjector = injector.createChildInjector(childAnnotatedComponent);

    verify(parentComponent).configureInjector((LightsaberInjector) injector);
    verifyNoMoreInteractions(parentComponent);
    verify(childAnnotatedComponent).configureInjector((LightsaberInjector) childInjector);
    verifyNoMoreInteractions(childAnnotatedComponent);
    final Named annotation = createNamedAnnotation("Annotated");
    assertSame(injector, injector.getInstance(com.joom.lightsaber.Key.of(com.joom.lightsaber.Injector.class)));
    assertSame(childInjector, childInjector.getInstance(com.joom.lightsaber.Key.of(com.joom.lightsaber.Injector.class)));
    assertEquals("Parent String", childInjector.getInstance(String.class));
    assertEquals("Parent String", childInjector.getInstance(com.joom.lightsaber.Key.of(String.class)));
    assertEquals("Child Annotated String", childInjector.getInstance(com.joom.lightsaber.Key.of(String.class, annotation)));
  }

  @Test
  public void testInjectionInterceptor() {
    @SuppressWarnings("unchecked")
    final Provider<String> stringProvider = mock(Provider.class);
    when(stringProvider.get()).thenReturn("StringInstanceClass", "StringInstanceKey", "StringProviderClass", "StringProviderKey");
    @SuppressWarnings("unchecked")
    final Provider<Object> objectProvider = mock(Provider.class);
    when(objectProvider.get()).thenReturn("ObjectInstanceClass", "ObjectInstanceKey", "ObjectProviderClass", "ObjectProviderKey");
    final ProviderInterceptor interceptor = new ProviderInterceptorBuilder()
        .addProviderForClass(String.class, stringProvider)
        .addProviderForClass(Object.class, objectProvider)
        .build();

    final com.joom.lightsaber.Lightsaber lightsaber = new Lightsaber.Builder().addProviderInterceptor(interceptor).build();
    final InjectorConfigurator parentComponent = createParentComponent();
    final InjectorConfigurator childAnnotatedComponent = createChildAnnotatedComponent();

    final com.joom.lightsaber.Injector injector = lightsaber.createInjector(parentComponent);
    final Injector childInjector = injector.createChildInjector(childAnnotatedComponent);

    assertEquals("StringInstanceClass", childInjector.getInstance(String.class));
    assertEquals("StringInstanceKey", childInjector.getInstance(com.joom.lightsaber.Key.of(String.class)));
    assertEquals("StringProviderClass", childInjector.getProvider(String.class).get());
    assertEquals("StringProviderKey", childInjector.getProvider(com.joom.lightsaber.Key.of(String.class)).get());

    assertEquals("ObjectInstanceClass", childInjector.getInstance(Object.class));
    assertEquals("ObjectInstanceKey", childInjector.getInstance(com.joom.lightsaber.Key.of(Object.class)));
    assertEquals("ObjectProviderClass", childInjector.getProvider(Object.class).get());
    assertEquals("ObjectProviderKey", childInjector.getProvider(com.joom.lightsaber.Key.of(Object.class)).get());

    final Named annotation = createNamedAnnotation("Annotated");
    assertEquals("Child Annotated String", childInjector.getInstance(com.joom.lightsaber.Key.of(String.class, annotation)));
  }

  private static InjectorConfigurator createParentComponent() {
    final InjectorConfigurator configurator = mock(InjectorConfigurator.class);
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(final InvocationOnMock invocation) {
        final LightsaberInjector injector = (LightsaberInjector) invocation.getArguments()[0];
        injector.registerProvider(String.class, new Provider<String>() {
          @Nonnull
          @Override
          public String get() {
            return "Parent String";
          }
        });
        return null;
      }
    })
        .when(configurator).configureInjector(any(LightsaberInjector.class));
    return configurator;
  }

  private static InjectorConfigurator createChildComponent() {
    final InjectorConfigurator configurator = mock(InjectorConfigurator.class);
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(final InvocationOnMock invocation) {
        final LightsaberInjector injector = (LightsaberInjector) invocation.getArguments()[0];
        injector.registerProvider(com.joom.lightsaber.Key.of(Object.class), new Provider<Object>() {
          @Nonnull
          @Override
          public Object get() {
            return "Child Object";
          }
        });
        return null;
      }
    })
        .when(configurator).configureInjector(any(LightsaberInjector.class));
    return configurator;
  }

  private static InjectorConfigurator createChildAnnotatedComponent() {
    final InjectorConfigurator configurator = mock(InjectorConfigurator.class);
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(final InvocationOnMock invocation) {
        final LightsaberInjector injector = (LightsaberInjector) invocation.getArguments()[0];
        injector.registerProvider(Key.of(String.class, createNamedAnnotation("Annotated")),
            new Provider<String>() {
              @Nonnull
              @Override
              public String get() {
                return "Child Annotated String";
              }
            });
        return null;
      }
    })
        .when(configurator).configureInjector(any(LightsaberInjector.class));
    return configurator;
  }

  private static Named createNamedAnnotation(@SuppressWarnings("SameParameterValue") final String value) {
    return new AnnotationBuilder<Named>(Named.class).addMember("value", value).build();
  }
}
