/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.testapi;

import java.util.TimeZone;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class TemporaryDefaultTimeZone implements TestRule {

  private final TimeZone targetTimeZone;

  private TemporaryDefaultTimeZone(final TimeZone targetTimeZone) {
    this.targetTimeZone = targetTimeZone;
  }

  public static TemporaryDefaultTimeZone toBe(final String timezone) {
    return toBe(TimeZone.getTimeZone(timezone));
  }

  public static TemporaryDefaultTimeZone toBe(final TimeZone timezone) {
    return new TemporaryDefaultTimeZone(timezone);
  }

  public Statement apply(final Statement base, final Description description) {
    return new Statement() {
      public void evaluate() throws Throwable {
        final TimeZone original = TimeZone.getDefault();

        try {
          TimeZone.setDefault(targetTimeZone);
          base.evaluate();
        } finally {
          TimeZone.setDefault(original);
        }
      }
    };
  }

}