/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.conditions;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.jma.conditions.UsageThresholdCondition.UsageThresholdConditionViolatedException;
import com.sap.jma.configuration.IncreaseOverTimeFrameUsageThresholdConfiguration;
import com.sap.jma.configuration.IntervalTimeUnit;
import com.sap.jma.logging.Logger;
import com.sap.jma.time.Clock;
import com.sap.jma.vms.MemoryPool;
import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class IncreaseOverTimeFrameThresholdConditionTest {

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  private final Clock clock = mock(Clock.class);

  private final MemoryPool memoryPool = mock(MemoryPool.class);

  private final MemoryUsage memoryUsage = mock(MemoryUsage.class);

  private final Logger logger = mock(Logger.class);

  private static TypeSafeMatcher<IncreaseOverTimeFrameUsageThresholdCondition>
      hasMeasurementPeriodInMillis(final long millis) {
    return new TypeSafeMatcher<IncreaseOverTimeFrameUsageThresholdCondition>() {
      @Override
      protected boolean matchesSafely(
          IncreaseOverTimeFrameUsageThresholdCondition condition) {
        return millis == condition.measurementPeriod;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("has a measurement period of '" + millis + "' millis");
      }
    };
  }

  @Before
  public void setup() {
    doReturn("TestPool").when(memoryPool).getName();
    doReturn(memoryUsage).when(memoryPool).getMemoryUsage();
  }

  @Test
  public void testMeasurementPeriod() throws Exception {
    assertThat(createCondition(20d, 4.8d, TimeUnit.SECONDS),
        hasMeasurementPeriodInMillis(2400L));
    assertThat(createCondition(20d, 2, TimeUnit.MINUTES),
        hasMeasurementPeriodInMillis(1000L * 60));
    assertThat(createCondition(20d, 1, TimeUnit.HOURS),
        hasMeasurementPeriodInMillis(1000L * 60 * 30));
  }

  @Test
  public void testMeasurementPointAccumulationWithImmediateViolation() throws Exception {
    when(clock.getMillis()).thenReturn(400L, 1500L, 3400L);
    doReturn(100L).when(memoryUsage).getMax();
    when(memoryUsage.getUsed()).thenReturn(10L, 50L);

    final IncreaseOverTimeFrameUsageThresholdCondition condition =
        createCondition(20d, 3d, TimeUnit.SECONDS);

    assertThat(condition, hasMeasurementPeriodInMillis(1500L));

    condition.evaluate();
    verify(logger).debug("First measurement for memory pool '%s'", "TestPool");

    assertThat(condition.measurements, hasSize(1));
    final IncreaseOverTimeFrameUsageThresholdCondition.Measurement firstPoint =
        condition.measurements.peek();
    assertThat(firstPoint.getTimestamp(), is(400L));

    // 2nd eval should not add a measurement point, too early
    condition.evaluate();

    assertThat(condition.measurements, hasSize(1));
    assertThat(condition.measurements.peek(), sameInstance(firstPoint));

    // 3rd eval should add a measurement point, perform check, and fail
    expectedException.expect(UsageThresholdConditionViolatedException.class);
    expectedException.expectMessage(is("Memory pool 'TestPool' at 50% usage, increased from 10% "
        + "by more than maximum 20% increase (actual increase: 40%) over the last 3.0s"));
    try {
      condition.evaluate();
    } finally {
      assertThat(condition.measurements, hasSize(2));
      assertThat(condition.measurements.getFirst(), sameInstance(firstPoint));

      final IncreaseOverTimeFrameUsageThresholdCondition.Measurement secondPoint =
          condition.measurements.getLast();
      assertThat(secondPoint.getTimestamp(), is(3400L));
    }
  }

  @Test
  public void testMeasurementPointAccumulationWithoutViolation() throws Exception {
    doReturn(100L).when(memoryUsage).getMax();
    when(memoryUsage.getUsed()).thenReturn(10L, 5L, 24L, 23L);
    when(clock.getMillis()).thenReturn(400L, 1901L, 3402L, 5002L);

    final IncreaseOverTimeFrameUsageThresholdCondition condition =
        createCondition(20d, 3d, TimeUnit.SECONDS);

    assertThat(condition, hasMeasurementPeriodInMillis(1500L));

    // 1st eval: collect first measurement and do nothing else
    condition.evaluate();

    assertThat(condition.measurements, hasSize(1));
    final IncreaseOverTimeFrameUsageThresholdCondition.Measurement firstPoint =
        condition.measurements.peek();
    assertThat(firstPoint.getTimestamp(), is(400L));

    verify(logger).debug("First measurement for memory pool '%s'", "TestPool");

    // 2nd eval should not add a measurement point, too early to trigger dump
    condition.evaluate();

    assertThat(condition.measurements, hasSize(2));
    assertThat(condition.measurements.peek(), sameInstance(firstPoint));
    final IncreaseOverTimeFrameUsageThresholdCondition.Measurement secondPoint =
        condition.measurements.getLast();
    assertThat(secondPoint.getTimestamp(), is(1901L));

    verify(logger).debug("Memory pool '%s' at %s%% usage, changed from %s%% by less than maximum "
        + "%s%% increase (actual increase: %s%%) over the last %s%s", "TestPool", "5", "10", "20",
        "-5", 1.5D, "s");

    /*
     * 3rd eval should not add a measurement point as not enough time has elapsed before
     * the previous one
     */
    condition.evaluate();

    assertThat(condition.measurements, hasSize(3));
    assertThat(condition.measurements.getFirst(), sameInstance(firstPoint));
    final IncreaseOverTimeFrameUsageThresholdCondition.Measurement thirdPoint =
        condition.measurements.getLast();
    assertThat(thirdPoint.getTimestamp(), is(3402L));

    verify(logger).debug("Memory pool '%s' at %s%% usage, changed from %s%% by less than maximum "
        + "%s%% increase (actual increase: %s%%) over the last %s%s", "TestPool", "24", "10", "20",
        "14", 3.0D, "s");

    /*
     * 4th eval should remove first measurement point, perform check, and not fail because
     * usage not changed
     */
    condition.evaluate();
    assertThat(condition.measurements, hasSize(3));
    assertThat(condition.measurements.getFirst(), sameInstance(secondPoint));
    final IncreaseOverTimeFrameUsageThresholdCondition.Measurement fourthPoint =
        condition.measurements.getLast();
    assertThat(fourthPoint.getTimestamp(), is(5002L));

    verify(logger).debug("Memory pool '%s' at %s%% usage, changed from %s%% by less than maximum "
        + "%s%% increase (actual increase: %s%%) over the last %s%s", "TestPool", "23", "5", "20",
        "18", 3.1D, "s");
  }

  private IncreaseOverTimeFrameUsageThresholdCondition createCondition(final double delta,
                                                                       final double period,
                                                                       final TimeUnit timeUnit)
      throws Exception {
    return createCondition(delta, period, timeUnit, memoryPool);
  }

  private IncreaseOverTimeFrameUsageThresholdCondition
      createCondition(final double delta, final double period,
                      final TimeUnit timeUnit, final MemoryPool memoryPool)
      throws Exception {
    final IncreaseOverTimeFrameUsageThresholdConfiguration configuration =
        IncreaseOverTimeFrameUsageThresholdConfiguration.parse(memoryPool.getType(),
            "+" + String.valueOf(delta) + "%/" + String.valueOf(period)
            + IntervalTimeUnit.from(timeUnit).getLiteral());
    return new IncreaseOverTimeFrameUsageThresholdCondition(configuration, memoryPool, logger) {
      @Override
      protected Clock getClock() {
        return clock;
      }
    };
  }

}
