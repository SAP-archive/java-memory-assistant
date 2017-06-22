/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.configuration;

import com.sap.jma.vms.JavaVirtualMachine;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * TODO Refactor to unify with ExecutionFrequency?
 */
public class IncreaseOverTimeFrameUsageThresholdConfiguration
    implements UsageThresholdConfiguration {

  private static final Pattern INCREASE_OVER_TIME_FRAME_PATTERN =
      Pattern.compile("\\+(\\d*\\.?\\d*\\d)%/(\\d*\\.?\\d*\\d)(ms|s|m|h)");

  private final JavaVirtualMachine.MemoryPoolType memoryPool;
  private final double delta;
  private final double timeFrame;
  private final IntervalTimeUnit timeUnit;

  IncreaseOverTimeFrameUsageThresholdConfiguration(final JavaVirtualMachine.MemoryPoolType memoryPool,
                                                   final double delta,
                                                   final double timeFrame,
                                                   final IntervalTimeUnit timeUnit) {
    this.memoryPool = memoryPool;
    this.delta = delta;
    this.timeFrame = timeFrame;
    this.timeUnit = timeUnit;
  }

  public static IncreaseOverTimeFrameUsageThresholdConfiguration parse(
      final JavaVirtualMachine.MemoryPoolType memoryPool,
      final String value)
      throws InvalidPropertyValueException {
    final Matcher matcher = INCREASE_OVER_TIME_FRAME_PATTERN.matcher(value);

    if (!matcher.matches()) {
      throw new InvalidPropertyValueException(
          String.format("it must follow the Java pattern '%s'",
              INCREASE_OVER_TIME_FRAME_PATTERN.pattern()));
    }

    final String deltaString = matcher.group(1);
    if (deltaString.isEmpty()) {
      throw new InvalidPropertyValueException(
          String.format("it must follow the Java pattern '%s' and have at "
                  + "least a digit before the '%%' sign",
              INCREASE_OVER_TIME_FRAME_PATTERN.pattern()));
    }

    final double delta;
    try {
      delta = Double.parseDouble(deltaString);

      if (delta <= 0) {
        throw new NumberFormatException();
      }
    } catch (final NumberFormatException ex) {
      throw new InvalidPropertyValueException(
          String.format("The value '%s' is not valid for the increase on memory usage in "
                  + "the time-frame: must be a positive Java double (0 < n <= %.2f)",
              matcher.group(1), Double.MAX_VALUE), new NumberFormatException());
    }

    final String timeFrameValue = matcher.group(2);
    final double timeFrameInt;
    if (timeFrameValue.isEmpty()) {
      timeFrameInt = 1;
    } else {
      try {
        timeFrameInt = Double.parseDouble(timeFrameValue);

        if (timeFrameInt < 1) {
          throw new NumberFormatException();
        }
      } catch (final NumberFormatException ex) {
        throw new InvalidPropertyValueException(
            String.format("The value '%s' is not valid for the time-frame of memory usage "
                    + "increase threshold: must be a positive Java integer (0 < n <= 2147483647)",
                timeFrameValue), new NumberFormatException());
      }
    }

    final IntervalTimeUnit timeFrameUnit;
    try {
      timeFrameUnit = IntervalTimeUnit.from(matcher.group(3));
    } catch (final NoSuchElementException ex) {
      final StringBuilder values = new StringBuilder();
      for (final IntervalTimeUnit unit : IntervalTimeUnit.values()) {
        values.append(unit.getLiteral());
        values.append(',');
        values.append(' ');
      }
      // Drop last ", "
      values.setLength(values.length() - 2);

      throw new InvalidPropertyValueException(
          String.format("The value '%s' is not valid for the time unit of "
              + "the time-frame of memory usage increase threshold: valid values are "
              + values, timeFrameValue));
    }

    return new IncreaseOverTimeFrameUsageThresholdConfiguration(memoryPool, delta, timeFrameInt,
        timeFrameUnit);
  }

  @Override
  public JavaVirtualMachine.MemoryPoolType getMemoryPool() {
    return memoryPool;
  }

  public double getDelta() {
    return delta;
  }

  public double getTimeFrame() {
    return timeFrame;
  }

  public IntervalTimeUnit getTimeUnit() {
    return timeUnit;
  }
}
