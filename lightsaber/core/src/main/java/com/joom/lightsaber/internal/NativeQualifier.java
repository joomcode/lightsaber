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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Creates canonical instances of marker qualifier annotations for native generated keys. */
public final class NativeQualifier {
  private static final ConcurrentMap<Class<? extends Annotation>, Annotation> INSTANCES = new ConcurrentHashMap<>();

  private NativeQualifier() {
  }

  public static Annotation of(final Class<? extends Annotation> annotationType) {
    if (annotationType.getDeclaredMethods().length != 0) {
      throw new IllegalArgumentException("Native qualifier must be a marker annotation: " + annotationType.getName());
    }
    return INSTANCES.computeIfAbsent(annotationType, NativeQualifier::create);
  }

  public static Annotation of(
      final Class<? extends Annotation> annotationType,
      final String[] memberNames,
      final Object[] memberValues
  ) {
    if (memberNames.length != memberValues.length) {
      throw new IllegalArgumentException("Qualifier member names and values must have the same size");
    }
    final Map<String, Object> values = new HashMap<>();
    for (int index = 0; index < memberNames.length; index++) {
      values.put(memberNames[index], cloneArray(memberValues[index]));
    }
    for (final Method method : annotationType.getDeclaredMethods()) {
      if (!values.containsKey(method.getName())) {
        values.put(method.getName(), cloneArray(method.getDefaultValue()));
      }
    }
    return create(annotationType, values);
  }

  private static Annotation create(final Class<? extends Annotation> annotationType) {
    final ClassLoader classLoader = annotationType.getClassLoader() != null
        ? annotationType.getClassLoader()
        : NativeQualifier.class.getClassLoader();
    return (Annotation) Proxy.newProxyInstance(
        classLoader,
        new Class<?>[]{annotationType},
        new MarkerAnnotationHandler(annotationType)
    );
  }

  private static Annotation create(
      final Class<? extends Annotation> annotationType,
      final Map<String, Object> values
  ) {
    final ClassLoader classLoader = annotationType.getClassLoader() != null
        ? annotationType.getClassLoader()
        : NativeQualifier.class.getClassLoader();
    return (Annotation) Proxy.newProxyInstance(
        classLoader,
        new Class<?>[]{annotationType},
        new ValuedAnnotationHandler(annotationType, values)
    );
  }

  private static Object cloneArray(final Object value) {
    if (value == null || !value.getClass().isArray()) {
      return value;
    }
    if (value instanceof boolean[]) return ((boolean[]) value).clone();
    if (value instanceof byte[]) return ((byte[]) value).clone();
    if (value instanceof char[]) return ((char[]) value).clone();
    if (value instanceof double[]) return ((double[]) value).clone();
    if (value instanceof float[]) return ((float[]) value).clone();
    if (value instanceof int[]) return ((int[]) value).clone();
    if (value instanceof long[]) return ((long[]) value).clone();
    if (value instanceof short[]) return ((short[]) value).clone();
    return ((Object[]) value).clone();
  }

  private static boolean memberEquals(final Object first, final Object second) {
    if (first instanceof boolean[] && second instanceof boolean[]) return Arrays.equals((boolean[]) first, (boolean[]) second);
    if (first instanceof byte[] && second instanceof byte[]) return Arrays.equals((byte[]) first, (byte[]) second);
    if (first instanceof char[] && second instanceof char[]) return Arrays.equals((char[]) first, (char[]) second);
    if (first instanceof double[] && second instanceof double[]) return Arrays.equals((double[]) first, (double[]) second);
    if (first instanceof float[] && second instanceof float[]) return Arrays.equals((float[]) first, (float[]) second);
    if (first instanceof int[] && second instanceof int[]) return Arrays.equals((int[]) first, (int[]) second);
    if (first instanceof long[] && second instanceof long[]) return Arrays.equals((long[]) first, (long[]) second);
    if (first instanceof short[] && second instanceof short[]) return Arrays.equals((short[]) first, (short[]) second);
    if (first instanceof Object[] && second instanceof Object[]) return Arrays.equals((Object[]) first, (Object[]) second);
    return Objects.equals(first, second);
  }

  private static int memberHashCode(final Object value) {
    if (value instanceof boolean[]) return Arrays.hashCode((boolean[]) value);
    if (value instanceof byte[]) return Arrays.hashCode((byte[]) value);
    if (value instanceof char[]) return Arrays.hashCode((char[]) value);
    if (value instanceof double[]) return Arrays.hashCode((double[]) value);
    if (value instanceof float[]) return Arrays.hashCode((float[]) value);
    if (value instanceof int[]) return Arrays.hashCode((int[]) value);
    if (value instanceof long[]) return Arrays.hashCode((long[]) value);
    if (value instanceof short[]) return Arrays.hashCode((short[]) value);
    if (value instanceof Object[]) return Arrays.hashCode((Object[]) value);
    return Objects.hashCode(value);
  }

  private static final class MarkerAnnotationHandler implements InvocationHandler {
    private final Class<? extends Annotation> annotationType;

    private MarkerAnnotationHandler(final Class<? extends Annotation> annotationType) {
      this.annotationType = annotationType;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] arguments) {
      switch (method.getName()) {
        case "annotationType":
          return annotationType;
        case "hashCode":
          return 0;
        case "toString":
          return "@" + annotationType.getName() + "()";
        case "equals":
          return arguments != null && arguments.length == 1 && annotationType.isInstance(arguments[0]);
        default:
          throw new IllegalStateException("Unexpected marker annotation method: " + method);
      }
    }
  }

  private static final class ValuedAnnotationHandler implements InvocationHandler {
    private final Class<? extends Annotation> annotationType;
    private final Map<String, Object> values;

    private ValuedAnnotationHandler(
        final Class<? extends Annotation> annotationType,
        final Map<String, Object> values
    ) {
      this.annotationType = annotationType;
      this.values = values;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] arguments) throws Exception {
      switch (method.getName()) {
        case "annotationType":
          return annotationType;
        case "hashCode":
          int hashCode = 0;
          for (final Method member : annotationType.getDeclaredMethods()) {
            hashCode += (127 * member.getName().hashCode()) ^ memberHashCode(values.get(member.getName()));
          }
          return hashCode;
        case "toString":
          return "@" + annotationType.getName() + values;
        case "equals":
          if (arguments == null || arguments.length != 1 || !annotationType.isInstance(arguments[0])) return false;
          for (final Method member : annotationType.getDeclaredMethods()) {
            if (!memberEquals(values.get(member.getName()), member.invoke(arguments[0]))) return false;
          }
          return true;
        default:
          if (values.containsKey(method.getName())) return cloneArray(values.get(method.getName()));
          throw new IllegalStateException("Unexpected annotation method: " + method);
      }
    }
  }
}
