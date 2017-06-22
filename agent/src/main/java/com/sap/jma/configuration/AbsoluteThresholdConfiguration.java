/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.configuration;

import com.sap.jma.vms.JavaVirtualMachine;
import com.sap.jma.vms.JavaVirtualMachine.MemoryPoolType;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbsoluteThresholdConfiguration implements ThresholdConfiguration {

  private static final Pattern ABSOLUTE_PATTERN =
      Pattern.compile("([<=>]+)(\\d*\\.?\\d*\\d)([KMG]?B)");

  public static AbsoluteThresholdConfiguration parse(final MemoryPoolType memoryPoolType,
                                                     final String value)
      throws InvalidPropertyValueException {
    final Matcher matcher = ABSOLUTE_PATTERN.matcher(value);

    if (!matcher.matches()) {
      throw new InvalidPropertyValueException(
          String.format("it must follow the Java pattern '%s'", ABSOLUTE_PATTERN.pattern()));
    }

    try {
      final Comparison comparison = Comparison.from(matcher.group(1));
      final double valueInUnitSize = Double.parseDouble(matcher.group(2));
      final MemorySizeUnit memorySizeUnit = MemorySizeUnit.from(matcher.group(3));

      final double valueInBytes = memorySizeUnit.toBytes(valueInUnitSize);

      return new AbsoluteThresholdConfiguration(memoryPoolType, comparison, valueInBytes,
          memorySizeUnit, value);
    } catch (final Exception ex) {
      throw new InvalidPropertyValueException("cannot be parsed", ex);
    }
  }

  private final MemoryPoolType memoryPool;
  private final Comparison comparison;
  private final double targetValueInBytes;
  private final MemorySizeUnit memorySizeUnit;
  private final String configurationValue;

  AbsoluteThresholdConfiguration(final MemoryPoolType memoryPool,
                                 final Comparison comparison,
                                 final double targetValueInBytes,
                                 final MemorySizeUnit memorySizeUnit,
                                 final String configurationValue) {
    this.memoryPool = memoryPool;
    this.comparison = comparison;
    this.targetValueInBytes = targetValueInBytes;
    this.memorySizeUnit = memorySizeUnit;
    this.configurationValue = configurationValue;
  }

  public Comparison getComparison() {
    return comparison;
  }

  public double getTargetValueInBytes() {
    return targetValueInBytes;
  }

  public MemorySizeUnit getMemorySizeUnit() {
    return memorySizeUnit;
  }

  @Override
  public MemoryPoolType getMemoryPool() {
    return memoryPool;
  }

  @Override
  public String toString() {
    return memoryPool + " " + configurationValue;
  }
}
