/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.configuration;

import com.sap.jma.vms.MemoryPool.Type;
import com.sap.jma.vms.MemoryPool;
import com.sap.jma.vms.PercentageUsageThresholdConditionImpl;
import com.sap.jma.vms.UsageThresholdCondition;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PercentageUsageThresholdConfiguration implements UsageThresholdConfiguration {

  private static final Pattern PERCENTAGE_PATTERN = Pattern.compile("(\\d*\\.?\\d*\\d)%");

  public static PercentageUsageThresholdConfiguration parse(final Type memoryPoolType,
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

    return new PercentageUsageThresholdConfiguration(memoryPoolType, f);
  }

  private final double value;
  private final Type memoryPool;

  public PercentageUsageThresholdConfiguration(final Type memoryPool,
                                               final double value) {
    this.memoryPool = memoryPool;
    this.value = value;
  }

  @Override
  public Type getMemoryPoolType() {
    return memoryPool;
  }

  public double getValue() {
    return value;
  }

  @Override
  public UsageThresholdCondition<PercentageUsageThresholdConfiguration> toCondition(
      final MemoryPool memoryPool) {
    return new PercentageUsageThresholdConditionImpl(this, memoryPool);
  }

}
