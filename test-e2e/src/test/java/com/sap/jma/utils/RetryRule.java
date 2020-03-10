/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.utils;

import java.util.LinkedList;
import java.util.List;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class RetryRule implements TestRule {

  public static RetryRule create(final int retryCount) {
    return new RetryRule(retryCount);
  }

  private final int retryCount;

  private RetryRule(final int retryCount) {
    this.retryCount = retryCount;
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        final List<Throwable> ts = new LinkedList<>();
        for (int i = 0; i < retryCount; ++i) {
          try {
            base.evaluate();
            return;
          } catch (final Throwable throwable) {
            ts.add(throwable);
          }
        }

        final Throwable throwable =
                new IllegalStateException("Test failed maximum amount of retries");

        for (final Throwable suppressedT : ts) {
          throwable.addSuppressed(suppressedT);
        }

        throw throwable;
      }
    };
  }
}