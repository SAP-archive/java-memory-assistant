/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.jma.Configuration;
import com.sap.jma.logging.Logger;
import com.sap.jma.time.Clock;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class IncreaseOverTimeFrameThresholdConditionImplTest {

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  private final Clock clock = mock(Clock.class);

  private final MemoryPoolMock memoryPool = mock(MemoryPoolMock.class);

  private final Logger logger = mock(Logger.class);

  private static TypeSafeMatcher<IncreaseOverTimeFrameThresholdConditionImpl>
      hasMeasurementPeriodInMillis(final long millis) {
    return new TypeSafeMatcher<IncreaseOverTimeFrameThresholdConditionImpl>() {
      @Override
      protected boolean matchesSafely(
          IncreaseOverTimeFrameThresholdConditionImpl increaseOverTimeFrameThresholdCondition) {
        return millis == increaseOverTimeFrameThresholdCondition.measurementPeriod;
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
    doReturn(100L).when(memoryPool).getMemoryMax();
    when(memoryPool.getMemoryUsed()).thenReturn(10L, 50L);

    final IncreaseOverTimeFrameThresholdConditionImpl condition =
        createCondition(20d, 3d, TimeUnit.SECONDS);

    assertThat(condition, hasMeasurementPeriodInMillis(1500L));

    condition.evaluate();
    verify(logger).debug("First measurement for memory pool 'TestPool'");

    assertThat(condition.measurements, hasSize(1));
    final IncreaseOverTimeFrameThresholdConditionImpl.Measurement firstPoint =
        condition.measurements.peek();
    assertThat(firstPoint.getTimestamp(), is(400L));

    // 2nd eval should not add a measurement point, too early
    condition.evaluate();

    assertThat(condition.measurements, hasSize(1));
    assertThat(condition.measurements.peek(), sameInstance(firstPoint));

    // 3rd eval should add a measurement point, perform check, and fail
    expectedException.expect(JavaVirtualMachine.UsageThresholdConditionViolatedException.class);
    expectedException.expectMessage(is("Memory pool 'TestPool' at 50% usage, increased from 10% "
        + "by more than maximum 20% increase (actual increase: 40%) over the last 3.0s"));
    try {
      condition.evaluate();
    } finally {
      assertThat(condition.measurements, hasSize(2));
      assertThat(condition.measurements.getFirst(), sameInstance(firstPoint));

      final IncreaseOverTimeFrameThresholdConditionImpl.Measurement secondPoint =
          condition.measurements.getLast();
      assertThat(secondPoint.getTimestamp(), is(3400L));
    }
  }

  @Test
  public void testMeasurementPointAccumulationWithoutViolation() throws Exception {
    doReturn(100L).when(memoryPool).getMemoryMax();
    when(memoryPool.getMemoryUsed()).thenReturn(10L, 5L, 24L, 23L);
    when(clock.getMillis()).thenReturn(400L, 1901L, 3402L, 5002L);

    final IncreaseOverTimeFrameThresholdConditionImpl condition =
        createCondition(20d, 3d, TimeUnit.SECONDS);

    assertThat(condition, hasMeasurementPeriodInMillis(1500L));

    // 1st eval: collect first measurement and do nothing else
    condition.evaluate();

    assertThat(condition.measurements, hasSize(1));
    final IncreaseOverTimeFrameThresholdConditionImpl.Measurement firstPoint =
        condition.measurements.peek();
    assertThat(firstPoint.getTimestamp(), is(400L));

    verify(logger).debug("First measurement for memory pool 'TestPool'");

    // 2nd eval should not add a measurement point, too early to trigger dump
    condition.evaluate();

    assertThat(condition.measurements, hasSize(2));
    assertThat(condition.measurements.peek(), sameInstance(firstPoint));
    final IncreaseOverTimeFrameThresholdConditionImpl.Measurement secondPoint =
        condition.measurements.getLast();
    assertThat(secondPoint.getTimestamp(), is(1901L));

    verify(logger).debug("Memory pool 'TestPool' at 5% usage, changed from 10% by less "
        + "than maximum 20% increase (actual increase: -5%) over the last 1.5s");

    /*
     * 3rd eval should not add a measurement point as not enough time has elapsed before
     * the previous one
     */
    condition.evaluate();

    assertThat(condition.measurements, hasSize(3));
    assertThat(condition.measurements.getFirst(), sameInstance(firstPoint));
    final IncreaseOverTimeFrameThresholdConditionImpl.Measurement thirdPoint =
        condition.measurements.getLast();
    assertThat(thirdPoint.getTimestamp(), is(3402L));

    verify(logger).debug("Memory pool 'TestPool' at 24% usage, changed from 10% by less "
        + "than maximum 20% increase (actual increase: 14%) over the last 3.0s");

    /*
     * 4th eval should remove first measurement point, perform check, and not fail because
     * usage not changed
     */
    condition.evaluate();
    assertThat(condition.measurements, hasSize(3));
    assertThat(condition.measurements.getFirst(), sameInstance(secondPoint));
    final IncreaseOverTimeFrameThresholdConditionImpl.Measurement fourthPoint =
        condition.measurements.getLast();
    assertThat(fourthPoint.getTimestamp(), is(5002L));

    verify(logger).debug("Memory pool 'TestPool' at 23% usage, changed from 5% by less "
        + "than maximum 20% increase (actual increase: 18%) over the last 3.1s");
  }

  private IncreaseOverTimeFrameThresholdConditionImpl createCondition(final double delta,
                                                                      final double period,
                                                                      final TimeUnit timeUnit)
      throws Exception {
    return createCondition(delta, period, timeUnit, memoryPool);
  }

  private IncreaseOverTimeFrameThresholdConditionImpl
      createCondition(final double delta, final double period,
                      final TimeUnit timeUnit, final MemoryPoolMock memoryPool)
      throws Exception {
    final Configuration.IncreaseOverTimeFrameThresholdConfiguration configuration =
        Configuration.IncreaseOverTimeFrameThresholdConfiguration.parse(memoryPool.getType(),
            "+" + String.valueOf(delta) + "%/" + String.valueOf(period)
            + Configuration.IntervalTimeUnit.from(timeUnit).getLiteral());
    return new IncreaseOverTimeFrameThresholdConditionImpl(logger) {

      @Override
      protected Clock getClock() {
        return clock;
      }

      @Override
      protected String getMemoryPoolName() {
        return memoryPool.getName();
      }

      @Override
      protected long getMemoryUsed() {
        return memoryPool.getMemoryUsed();
      }

      @Override
      protected long getMemoryMax() {
        return memoryPool.getMemoryMax();
      }

      @Override
      public Configuration.IncreaseOverTimeFrameThresholdConfiguration getUsageThreshold() {
        return configuration;
      }
    };
  }

  private interface MemoryPoolMock {
    JavaVirtualMachine.MemoryPoolType getType();

    String getName();

    long getMemoryUsed();

    long getMemoryMax();
  }

}
