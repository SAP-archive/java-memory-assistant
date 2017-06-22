/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import com.sap.jma.configuration.IncreaseOverTimeFrameThresholdConfiguration;
import com.sap.jma.logging.Logger;
import com.sap.jma.time.Clock;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

/*
 * TODO How to take into account the Garbage collection?
 * The more GC, the WORSE violating this condition means
 */
public abstract class IncreaseOverTimeFrameThresholdConditionImpl
    extends AbstractUsageThresholdConditionImpl<IncreaseOverTimeFrameThresholdConfiguration> {

  // VisibleForTesting
  final Deque<Measurement> measurements = new LinkedList<>();

  private final IncreaseOverTimeFrameThresholdConfiguration usageThreshold
      = getUsageThreshold();

  // VisibleForTesting
  final long measurementPeriod = usageThreshold.getTimeUnit()
      .toMilliSeconds(usageThreshold.getTimeFrame()) / 2;

  // VisibleForTesting
  private final Logger logger;

  public IncreaseOverTimeFrameThresholdConditionImpl() {
    this(Logger.Factory.get(JavaVirtualMachine.UsageThresholdCondition.class));
  }

  //VisibleForTesting
  IncreaseOverTimeFrameThresholdConditionImpl(final Logger logger) {
    this.logger = logger;
  }

  // VisibleForTesting
  protected Clock getClock() {
    return Clock.SYSTEM;
  }

  protected abstract long getMemoryUsed();

  protected abstract long getMemoryMax();

  private double getCurrentUsageRatio() {
    return getMemoryUsed() * 100d / getMemoryMax();
  }

  @Override
  public void evaluate() throws JavaVirtualMachine.UsageThresholdConditionViolatedException {
    final long now = getClock().getMillis();

    if (!measurements.isEmpty()) {
      if (measurements.getLast().getTimestamp() + measurementPeriod > now) {
        /*
         * Skip this measurement, not enough time has elapsed for us to
         * need another measurement point
         */
        return;
      }

      /*
       * Discard all measurements to be to old to matter; since the scheduling mechanism
       * of the JVM is not precise, we give a certain margin of error
       */
      final long minimumTimestamp = now - (long) (measurementPeriod * 2.5d);
      final Iterator<Measurement> i = measurements.iterator();
      while (i.hasNext()) {
        final Measurement measurement = i.next();

        if (minimumTimestamp <= measurement.getTimestamp()) {
          break;
        }

        if (i.hasNext()) {
          // Leave at least one measurement point
          i.remove();
        }
      }
    }

    final Measurement last = new Measurement(now, getCurrentUsageRatio());
    measurements.add(last);
    if (measurements.size() < 2) {
      logger.debug(String.format("First measurement for memory pool '%s'", getMemoryPoolName()));
      return;
    }

    final Measurement first = measurements.getFirst();
    final double actualIncrease = last.getUsage() - first.getUsage();
    final long actualTimeFrameInMillis = last.getTimestamp() - first.getTimestamp();
    if (actualIncrease >= usageThreshold.getDelta() && actualTimeFrameInMillis
        >= usageThreshold.getTimeUnit().toMilliSeconds(usageThreshold.getTimeFrame())) {
      throw new JavaVirtualMachine.UsageThresholdConditionViolatedException(
          String.format("Memory pool '%s' at %s%% usage, increased from %s%% by more "
                  + "than maximum %s%% increase (actual increase: %s%%) over the last %s%s",
              getMemoryPoolName(),
              DECIMAL_FORMAT.format(last.getUsage()),
              DECIMAL_FORMAT.format(first.getUsage()),
              DECIMAL_FORMAT.format(usageThreshold.getDelta()),
              DECIMAL_FORMAT.format(actualIncrease),
              usageThreshold.getTimeUnit().fromMilliseconds(actualTimeFrameInMillis),
              usageThreshold.getTimeUnit().getLiteral()));
    } else {
      logger.debug(String.format("Memory pool '%s' at %s%% usage, changed from %s%% by less "
              + "than maximum %s%% increase (actual increase: %s%%) over the last %s%s",
          getMemoryPoolName(), DECIMAL_FORMAT.format(last.getUsage()),
          DECIMAL_FORMAT.format(first.getUsage()),
          DECIMAL_FORMAT.format(usageThreshold.getDelta()),
          DECIMAL_FORMAT.format(actualIncrease),
          usageThreshold.getTimeUnit().fromMilliseconds(actualTimeFrameInMillis),
          usageThreshold.getTimeUnit().getLiteral()));
    }
  }

  // VisibleForTesting
  static class Measurement {

    private final long timestamp;

    private final double usage;

    private Measurement(final long timestamp, final double usage) {
      this.timestamp = timestamp;
      this.usage = usage;
    }

    long getTimestamp() {
      return timestamp;
    }

    public double getUsage() {
      return usage;
    }
  }

}