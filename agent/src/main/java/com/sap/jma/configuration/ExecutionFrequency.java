/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.configuration;

import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExecutionFrequency {

  private static final Pattern EXECUTION_FREQUENCY_PATTERN =
      Pattern.compile("(\\d+)/(\\d*)(ms|s|m|h)");

  private final int executionAmount;
  private final long timeFrameInMillis;
  protected final String spec;

  ExecutionFrequency(final int executionAmount, final long timeFrameInMillis,
                     final String spec) {
    this.executionAmount = executionAmount;
    this.timeFrameInMillis = timeFrameInMillis;
    this.spec = spec;
  }

  public int getExecutionAmount() {
    return executionAmount;
  }

  public long getTimeFrameInMillis() {
    return timeFrameInMillis;
  }

  public static ExecutionFrequency parse(final String value)
      throws InvalidPropertyValueException {
    final Matcher matcher = EXECUTION_FREQUENCY_PATTERN.matcher(value);

    if (!matcher.matches()) {
      throw new InvalidPropertyValueException(
          String.format("it must follow the Java pattern '%s'",
              EXECUTION_FREQUENCY_PATTERN.pattern()));
    }

    final int maxCount;
    try {
      maxCount = Integer.parseInt(matcher.group(1));

      if (maxCount < 1) {
        throw new NumberFormatException();
      }
    } catch (final NumberFormatException ex) {
      throw new InvalidPropertyValueException(
          String.format("The value '%s' is not valid for the max amount of heap dumps "
                  + "in a time-frame: must be a positive Java integer (0 < n <= 2147483647)",
              matcher.group(1)));
    }

    final String timeFrameValue = matcher.group(2);
    final int timeFrameInt;
    if (timeFrameValue.isEmpty()) {
      timeFrameInt = 1;
    } else {
      try {
        timeFrameInt = Integer.parseInt(timeFrameValue);

        if (timeFrameInt < 1) {
          throw new NumberFormatException();
        }
      } catch (final NumberFormatException ex) {
        throw new InvalidPropertyValueException(
            String.format("The value '%s' is not valid for the time-frame of heap dumps: "
                + "must be a positive Java integer (0 < n <= 2147483647)", timeFrameValue));
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
          String.format("The value '%s' is not valid for the time unit of the time-frame "
              + "of heap dumps: valid values are " + values, timeFrameValue));
    }

    return new ExecutionFrequency(maxCount,
        timeFrameUnit.toMilliSeconds(timeFrameInt), value);
  }

  // Side-effects. Ugly, but on JVM 7 every other option is uglier. Streams, we miss you!
  public void filterToRelevantEntries(final List<Date> executionHistory, final Date now) {
    final Date latestRelevantTimestamp = getEarliestRelevantTimestamp(now);

    final Iterator<Date> i = executionHistory.iterator();
    while (i.hasNext()) {
      final Date executionTimestamp = i.next();
      if (executionTimestamp.before(latestRelevantTimestamp)) {
        i.remove();
      }
    }

    Collections.sort(executionHistory);
  }

  public boolean canPerformExecution(final List<Date> executionHistory,
                              final Date newExecutionTime) {
    final Date latestRelevantTimestamp = getEarliestRelevantTimestamp(newExecutionTime);

    int count = 0;
    for (final Date executionTimestamp : executionHistory) {
      if (latestRelevantTimestamp.before(executionTimestamp)) {
        count += 1;
      }
    }

    return count < executionAmount;
  }

  private Date getEarliestRelevantTimestamp(Date now) {
    return new Date(now.getTime() - timeFrameInMillis);
  }

}
