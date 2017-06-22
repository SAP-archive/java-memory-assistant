/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.configuration;

import com.sap.jma.vms.JavaVirtualMachine;
import com.sap.jma.vms.JavaVirtualMachine.MemoryPoolType;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PercentageThresholdConfiguration implements ThresholdConfiguration {

  private static final Pattern PERCENTAGE_PATTERN = Pattern.compile("(\\d*\\.?\\d*\\d)%");
  private final double value;
  private final MemoryPoolType memoryPool;

  public PercentageThresholdConfiguration(final MemoryPoolType memoryPool, final double value) {
    this.memoryPool = memoryPool;
    this.value = value;
  }

  public static PercentageThresholdConfiguration parse(final MemoryPoolType memoryPoolType,
                                                final String value)
      throws InvalidPropertyValueException {
    final Matcher matcher = PERCENTAGE_PATTERN.matcher(value);

    if (!matcher.matches()) {
      throw new InvalidPropertyValueException(
          String.format("it must follow the Java pattern '%s'", PERCENTAGE_PATTERN.pattern()));
    }

    final String valueString = matcher.group(1);
    if (valueString.isEmpty()) {
      throw new InvalidPropertyValueException(
          String.format("it must follow the Java pattern '%s' and have at least "
              + "a digit before the '%%' sign", PERCENTAGE_PATTERN.pattern()));
    }

    final double f = Double.parseDouble(valueString);
    if (f < 0d || f > 100d) {
      throw new InvalidPropertyValueException(
          String.format("Usage threshold must be between 0f and 100f"),
          new NumberFormatException());
    }

    final BigDecimal bd = new BigDecimal(valueString);
    final int scale = bd.scale();
    if (scale > 2) {
      throw new InvalidPropertyValueException(
          String.format("Usage thresholds can be specified only to the second "
              + "decimal precision (e.g., 42.42)"), new NumberFormatException());
    }

    return new PercentageThresholdConfiguration(memoryPoolType, f);
  }

  @Override
  public MemoryPoolType getMemoryPool() {
    return memoryPool;
  }

  public double getValue() {
    return value;
  }

}
