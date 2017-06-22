/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.configuration;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public enum IntervalTimeUnit {

  MILLISECONDS("ms", 1, TimeUnit.MILLISECONDS),

  SECONDS("s", 1000, TimeUnit.SECONDS),

  MINUTES("m", 1000 * 60, TimeUnit.MINUTES),

  HOURS("h", 1000 * 60 * 60, TimeUnit.HOURS);

  static final Pattern INTERVAL_PATTERN = Pattern.compile("(\\d*\\.?\\d*\\d)(ms|s|m|h)");

  private final String literal;

  private final int millisMultiplier;

  private final TimeUnit timeUnit;

  IntervalTimeUnit(final String literal, final int millisMultiplier, final TimeUnit timeUnit) {
    this.literal = literal;
    this.millisMultiplier = millisMultiplier;
    this.timeUnit = timeUnit;
  }

  public static IntervalTimeUnit from(final String literal) throws NoSuchElementException {
    for (final IntervalTimeUnit unit : IntervalTimeUnit.values()) {
      if (unit.literal.equals(literal)) {
        return unit;
      }
    }

    throw new NoSuchElementException(String.format("The interval time unit '%s' is unknown",
        literal));
  }

  public static IntervalTimeUnit from(final TimeUnit timeUnit) {
    for (final IntervalTimeUnit itu : IntervalTimeUnit.values()) {
      if (itu.timeUnit == timeUnit) {
        return itu;
      }
    }

    throw new NoSuchElementException(
        String.format("No interval time unit mapped to TimeUnit '%s'", timeUnit.name()));
  }

  public String getLiteral() {
    return literal;
  }

  public double fromMilliseconds(final long timeFrameInMillis) {
    return Math.round(timeFrameInMillis * 100d / millisMultiplier) / 100d;
  }

  public long toMilliSeconds(final double value) {
    return Double.valueOf(Math.floor(value * millisMultiplier)).longValue();
  }

}
