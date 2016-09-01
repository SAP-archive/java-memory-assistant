/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.testapi;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.rules.ExternalResource;

@SuppressWarnings("nls")
public final class TemporarySystemProperties extends ExternalResource {

  private static Object NULL_VALUE = new Object();
  // VisibleForTesting
  final ConcurrentMap<String, Object> originalValues = new ConcurrentHashMap<>();
  private volatile TemporarySystemPropertySetterImpl currentOpenSetter;

  private TemporarySystemProperties() {
  }

  public static TemporarySystemProperties overlay() {
    return new TemporarySystemProperties();
  }

  public synchronized TemporarySystemPropertySetter set(final String key) {
    assertNoSetupUnderway();

    if (key == null) {
      throw new IllegalArgumentException("The provided key is null");
    }

    return currentOpenSetter = new TemporarySystemPropertySetterImpl(key) {
      @Override
      public void to(final String value) {
        final String systemPropertyValue = System.getProperty(key);
        originalValues.putIfAbsent(key, systemPropertyValue == null
            ? NULL_VALUE : systemPropertyValue);
        System.setProperty(key, value == null ? "" : value);
        currentOpenSetter = null;
      }
    };
  }

  private void assertNoSetupUnderway() {
    if (currentOpenSetter != null) {
      throw new IllegalStateException(String.format(
          "The TemporarySystemPropertySetter for key '%s' has not been finalized yet with "
          + "the call TemporarySystemPropertySetter.to(value)",
          currentOpenSetter.forKey()));
    }
  }

  @Override
  protected void before() {
    originalValues.clear();
  }

  @Override
  protected void after() {
    try {
      for (final Entry<String, Object> originalValueEntry : originalValues.entrySet()) {
        final String key = originalValueEntry.getKey();
        final String originalValue = originalValueEntry.getValue() == NULL_VALUE ? null
            : originalValueEntry.getValue().toString();
        if (originalValue == null) {
          System.getProperties().remove(key);
        } else {
          System.setProperty(key, originalValue);
        }
      }
    } finally {
      originalValues.clear();

      assertNoSetupUnderway();
    }
  }

  public interface TemporarySystemPropertySetter {
    void to(String value);
  }

  private abstract static class TemporarySystemPropertySetterImpl
      implements TemporarySystemPropertySetter {

    private final String key;

    TemporarySystemPropertySetterImpl(final String key) {
      this.key = key;
    }

    String forKey() {
      return key;
    }
  }

}