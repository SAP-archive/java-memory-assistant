/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import static com.sap.jma.configuration.ExecutionFrequency.parse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.sap.jma.conditions.UsageThresholdCondition;
import com.sap.jma.configuration.AbsoluteUsageThresholdConfiguration;
import com.sap.jma.configuration.Configuration;
import com.sap.jma.configuration.InvalidPropertyValueException;
import com.sap.jma.configuration.PercentageUsageThresholdConfiguration;
import com.sap.jma.logging.Logger;
import com.sap.jma.vms.JavaVirtualMachine;
import com.sap.jma.vms.MemoryPool;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

@RunWith(Enclosed.class)
public class MBeanMonitorTest {

  private static AbsoluteUsageThresholdConfiguration absoluteThresholdConfiguration(
      final String value) throws InvalidPropertyValueException {
    return absoluteThresholdConfiguration(MemoryPool.Type.HEAP, value);
  }

  private static AbsoluteUsageThresholdConfiguration absoluteThresholdConfiguration(
      final MemoryPool.Type memoryPool, final String value)
      throws InvalidPropertyValueException {
    return AbsoluteUsageThresholdConfiguration.parse(memoryPool, value);
  }

  private static PercentageUsageThresholdConfiguration percentageThresholdConfiguration(
      final double usageThreshold) {
    return percentageThresholdConfiguration(MemoryPool.Type.HEAP, usageThreshold);
  }

  private static PercentageUsageThresholdConfiguration percentageThresholdConfiguration(
      final MemoryPool.Type memoryPool, final double usageThreshold) {
    return new PercentageUsageThresholdConfiguration(memoryPool, usageThreshold);
  }

  public static class BasicTest {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    private final Configuration configuration = mock(Configuration.class);

    private final HeapDumpCreator heapDumpCreator = mock(HeapDumpCreator.class);

    private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);

    private final Logger logger = mock(Logger.class);

    private final JavaVirtualMachine jvm = mock(JavaVirtualMachine.class);

    private MBeanMonitor subject;

    @Before
    public void setup() throws Exception {
      subject = spy(new MBeanMonitor(heapDumpCreator, configuration,
          new Callable<ScheduledExecutorService>() {
            @Override
            public ScheduledExecutorService call() {
              return executor;
            }
          }, logger));

      doReturn(jvm).when(subject).currentJvm();
    }

    @Test
    public void testNoConditionsSpecified() throws Exception {
      doReturn(Collections.emptyList()).when(jvm).getMemoryPools();

      doReturn(1000L).when(configuration).getCheckIntervalInMillis();

      subject.start();

      verifyZeroInteractions(executor);
      verify(logger).warning(
          "No memory conditions have been specified; the agent will not perform checks");
    }

    @Test
    public void testNoCheckIntervalSpecifiedOneCondition() throws Exception {
      final MemoryPool memoryPool = mock(MemoryPool.class);
      final UsageThresholdCondition usageCondition = mock(UsageThresholdCondition.class);

      doReturn(Collections.singletonList(memoryPool)).when(jvm).getMemoryPools();
      doReturn(usageCondition).when(memoryPool).toCondition(configuration);
      doReturn(percentageThresholdConfiguration(42d)).when(usageCondition)
          .getUsageThresholdConfiguration();

      doReturn(-1L).when(configuration).getCheckIntervalInMillis();

      subject.start();

      verifyZeroInteractions(executor);
      verify(logger).error("One memory condition has been specified, but no check interval "
          + "has been provided; the heap-dump agent will not perform checks");
    }

    @Test
    public void testNoCheckIntervalSpecifiedTwoConditions() throws Exception {
      final MemoryPool memoryPool1 = mock(MemoryPool.class);
      final UsageThresholdCondition usageCondition1 = mock(UsageThresholdCondition.class);

      final MemoryPool memoryPool2 = mock(MemoryPool.class);
      final UsageThresholdCondition usageCondition2 = mock(UsageThresholdCondition.class);

      doReturn(usageCondition1).when(memoryPool1).toCondition(configuration);
      doReturn(percentageThresholdConfiguration(42d)).when(usageCondition1)
          .getUsageThresholdConfiguration();

      doReturn(usageCondition2).when(memoryPool2).toCondition(configuration);
      doReturn(percentageThresholdConfiguration(24d)).when(usageCondition2)
          .getUsageThresholdConfiguration();

      doReturn(Arrays.asList(memoryPool1, memoryPool2))
          .when(jvm).getMemoryPools();

      doReturn(-1L).when(configuration).getCheckIntervalInMillis();

      subject.start();

      verifyZeroInteractions(executor);
      verify(logger).error("2 memory conditions have been specified, but no check interval "
          + "has been provided; the heap-dump agent will not perform checks");
    }

    @Test
    public void testMaxFrequency() throws Exception {
      final Date d1 = new Date(100L);
      final Date d2 = new Date(200L);
      final Date d3 = new Date(251L);
      final Date d4 = new Date(300L);

      doReturn(parse("1/150ms")).when(configuration).getMaxFrequency();
      when(subject.getCurrentDate()).thenReturn(d1, d2, d3, d4);

      subject.triggerHeapDump();
      subject.triggerHeapDump();
      subject.triggerHeapDump();
      subject.triggerHeapDump();

      verify(heapDumpCreator).createHeapDump(d1);
      verify(heapDumpCreator, never()).createHeapDump(d2);
      verify(heapDumpCreator).createHeapDump(d3);
      verify(heapDumpCreator, never()).createHeapDump(d4);
    }

  }

}
