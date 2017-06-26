/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import static com.sap.jma.configuration.ExecutionFrequency.parse;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.sap.jma.configuration.AbsoluteUsageThresholdConfiguration;
import com.sap.jma.configuration.Configuration;
import com.sap.jma.configuration.InvalidPropertyValueException;
import com.sap.jma.configuration.PercentageUsageThresholdConfiguration;
import com.sap.jma.logging.Logger;
import com.sap.jma.testapi.Matchers;
import com.sap.jma.vms.JavaVirtualMachine;
import com.sap.jma.vms.MemoryPool;
import com.sap.jma.vms.PercentageUsageThresholdCondition;
import com.sap.jma.vms.UsageThresholdCondition;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Before;
import org.junit.Ignore;
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

      doReturn(jvm).when(subject).findCurrentJvm();
    }

    @Test
    public void testNoConditionsSpecified() throws Exception {
      doReturn(Collections.emptyList()).when(subject).getMemoryPoolMxBeans();

      doReturn(1000L).when(configuration).getCheckIntervalInMillis();

      subject.start();

      verifyZeroInteractions(executor);
      verify(logger).warning(
          "No memory conditions have been specified; the agent will not perform checks");
    }

    @Test
    public void testNoCheckIntervalSpecifiedOneCondition() throws Exception {
      final MemoryPool memoryPool = mock(MemoryPool.class);
      final MemoryPoolMXBean memoryPoolBean = mock(MemoryPoolMXBean.class);
      final UsageThresholdCondition usageCondition = mock(UsageThresholdCondition.class);

      doReturn(Collections.singletonList(memoryPoolBean)).when(subject).getMemoryPoolMxBeans();
      doReturn(memoryPool).when(jvm).getMemoryPool(memoryPoolBean);
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
      final MemoryPoolMXBean memoryPoolBean1 = mock(MemoryPoolMXBean.class);
      final UsageThresholdCondition usageCondition1 = mock(UsageThresholdCondition.class);

      final MemoryPool memoryPool2 = mock(MemoryPool.class);
      final MemoryPoolMXBean memoryPoolBean2 = mock(MemoryPoolMXBean.class);
      final UsageThresholdCondition usageCondition2 = mock(UsageThresholdCondition.class);

      doReturn(memoryPool1).when(jvm).getMemoryPool(memoryPoolBean1);
      doReturn(usageCondition1).when(memoryPool1).toCondition(configuration);
      doReturn(percentageThresholdConfiguration(42d)).when(usageCondition1)
          .getUsageThresholdConfiguration();

      doReturn(memoryPool2).when(jvm).getMemoryPool(memoryPoolBean2);
      doReturn(usageCondition2).when(memoryPool2).toCondition(configuration);
      doReturn(percentageThresholdConfiguration(24d)).when(usageCondition2)
          .getUsageThresholdConfiguration();

      doReturn(Arrays.asList(memoryPoolBean1, memoryPoolBean2))
          .when(subject).getMemoryPoolMxBeans();

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

  public static class UnsupportedJvm {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    private final Configuration configuration = mock(Configuration.class);

    private final HeapDumpCreator heapDumpCreator = mock(HeapDumpCreator.class);

    private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);

    private final Logger logger = mock(Logger.class);

    private final RuntimeMXBean runtimeBean = mock(RuntimeMXBean.class);

    private MBeanMonitor subject;

    @Before
    public void setup() {
      subject = spy(new MBeanMonitor(heapDumpCreator, configuration,
          new Callable<ScheduledExecutorService>() {
            @Override
            public ScheduledExecutorService call() {
              return executor;
            }
          }, logger));

      doReturn(runtimeBean).when(subject).getRuntimeMxBean();
    }

    @Test
    public void testUnsupportedJvm() throws Exception {
      doReturn("Neverland").when(runtimeBean).getVmVendor();
      doReturn("1234").when(runtimeBean).getSpecVersion();

      expectedException.expect(JavaVirtualMachine.UnsupportedJavaVirtualMachineException.class);
      expectedException.expectMessage(
          is("JVM with vendor 'Neverland' and spec version '1234' is not supported"));

      subject.start();
    }

  }

  public static class SapJvm7x extends AbstractJvmTest {

    public SapJvm7x() {
      super(JavaVirtualMachine.Supported.SAP_7_X);
    }

    @Test
    public void testCompressedClassSpaceTriggersDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getCompressedClassSpaceMemoryUsageThreshold();
      addMemoryPoolBean("Compressed Class Space", 50L, 30L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'Compressed Class Space' "
            + "at 60% usage, configured threshold is 20%")));
    }

    @Test
    public void testCompressedClassSpaceDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getCompressedClassSpaceMemoryUsageThreshold();
      addMemoryPoolBean("Compressed Class Space", 50L, 1L);

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testCompressedClassSpaceThresholdNotConfigured() throws Exception {
      addMemoryPoolBean("Compressed Class Space", 50L, 50L);

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

    @Test
    public void testMetaspaceTriggersDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getMetaspaceMemoryUsageThreshold();
      addMemoryPoolBean("Metaspace", 50L, 30L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'Metaspace' at 60% usage, "
            + "configured threshold is 20%")));
    }

    @Test
    public void testMetaspaceDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getMetaspaceMemoryUsageThreshold();
      addMemoryPoolBean("Metaspace", 50L, 1L);

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testMetaspaceThresholdNotConfigured() throws Exception {
      addMemoryPoolBean("Metaspace", 50L, 50L);

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

  }

  public static class SapJvm8x extends AbstractJvmTest {

    public SapJvm8x() {
      super(JavaVirtualMachine.Supported.SAP_8_X);
    }

    @Test
    public void testCompressedClassSpaceTriggersDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getCompressedClassSpaceMemoryUsageThreshold();
      addMemoryPoolBean("Compressed Class Space", 50L, 30L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'Compressed Class Space' "
            + "at 60% usage, configured threshold is 20%")));
    }

    @Test
    public void testCompressedClassSpaceDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getCompressedClassSpaceMemoryUsageThreshold();
      addMemoryPoolBean("Compressed Class Space", 50L, 1L);

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testCompressedClassSpaceThresholdNotConfigured() throws Exception {
      addMemoryPoolBean("Compressed Class Space", 50L, 50L);

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

    @Test
    public void testMetaspaceTriggersDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getMetaspaceMemoryUsageThreshold();
      addMemoryPoolBean("Metaspace", 50L, 30L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'Metaspace' at 60% usage, "
            + "configured threshold is 20%")));
    }

    @Test
    public void testMetaspaceDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getMetaspaceMemoryUsageThreshold();
      addMemoryPoolBean("Metaspace", 50L, 1L);

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testMetaspaceThresholdNotConfigured() throws Exception {
      addMemoryPoolBean("Metaspace", 50L, 50L);

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

  }

  public static class OracleJdk7x extends AbstractJvmTest {

    public OracleJdk7x() {
      super(JavaVirtualMachine.Supported.ORACLE_7_X);
    }

    @Test
    public void testPermGenTriggersDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getPermGenMemoryUsageThreshold();
      addMemoryPoolBean("PS Perm Gen", 50L, 30L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'PS Perm Gen' "
              + "at 60% usage, configured threshold is 20%")));
    }

    @Test
    public void testPermGenDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getPermGenMemoryUsageThreshold();
      addMemoryPoolBean("PS Perm Gen", 50L, 1L);

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testPermGenThresholdNotConfigured() throws Exception {
      addMemoryPoolBean("PS Perm Gen", 50L, 50L);

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

  }

  public static class OracleJdk8x extends AbstractJvmTest {

    public OracleJdk8x() {
      super(JavaVirtualMachine.Supported.ORACLE_8_X);
    }

    @Test
    public void testCompressedClassSpaceTriggersDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getCompressedClassSpaceMemoryUsageThreshold();
      addMemoryPoolBean("Compressed Class Space", 50L, 30L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'Compressed Class Space' "
              + "at 60% usage, configured threshold is 20%")));
    }

    @Test
    public void testCompressedClassSpaceDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getCompressedClassSpaceMemoryUsageThreshold();
      addMemoryPoolBean("Compressed Class Space", 50L, 1L);

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testCompressedClassSpaceThresholdNotConfigured() throws Exception {
      addMemoryPoolBean("Compressed Class Space", 50L, 50L);

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

    @Test
    public void testMetaspaceTriggersDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getMetaspaceMemoryUsageThreshold();
      addMemoryPoolBean("Metaspace", 50L, 30L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'Metaspace' "
              + "at 60% usage, configured threshold is 20%")));
    }

    @Test
    public void testMetaspaceDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getMetaspaceMemoryUsageThreshold();
      addMemoryPoolBean("Metaspace", 50L, 1L);

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testMetaspaceThresholdNotConfigured() throws Exception {
      addMemoryPoolBean("Metaspace", 50L, 50L);

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

  }

  /*
   * Setup and tests common to all JDKs we support
   */
  @Ignore
  public abstract static class AbstractJvmTest {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    protected final MemoryUsage heapMemoryUsage = mock(MemoryUsage.class);

    protected final MemoryMXBean memoryBean = mock(MemoryMXBean.class);

    protected final List<MemoryPoolMXBean> memoryPoolBeans = new ArrayList<>();

    protected final Configuration configuration = mock(Configuration.class);

    protected final HeapDumpCreator heapDumpCreator = mock(HeapDumpCreator.class);

    protected final Logger logger = mock(Logger.class);

    protected final JavaVirtualMachine jvm;

    protected DeterministicScheduler scheduler;

    protected MBeanMonitor subject;

    private AbstractJvmTest(JavaVirtualMachine jvm) {
      this.jvm = jvm;
    }

    @Before
    public void setup() throws Exception {
      memoryPoolBeans.clear();

      doReturn(0L).when(configuration).getCheckIntervalInMillis();

      doReturn(heapMemoryUsage).when(memoryBean).getHeapMemoryUsage();

      scheduler = spy(new DeterministicScheduler());
      doReturn(Collections.emptyList()).when(scheduler).shutdownNow();

      subject = spy(new MBeanMonitor(heapDumpCreator, configuration,
          new Callable<ScheduledExecutorService>() {
            @Override
            public ScheduledExecutorService call() throws Exception {
              return scheduler;
            }
          }, logger) {
        @Override
        MemoryMXBean getMemoryMxBean() {
          return memoryBean;
        }

        @Override
        List<MemoryPoolMXBean> getMemoryPoolMxBeans() {
          return memoryPoolBeans;
        }
      });

      doReturn(jvm).when(subject).findCurrentJvm();
    }

    @Test
    public void testStart() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getHeapMemoryUsageThreshold();

      doReturn(5L).when(configuration).getCheckIntervalInMillis();

      assertThat(subject.isStarted(), is(false));

      subject.start();

      assertThat(subject.isStarted(), is(true));

      subject.start();

      assertThat(subject.isStarted(), is(true));
      verify(scheduler, times(1)).scheduleWithFixedDelay(any(Runnable.class), eq(5L), eq(5L),
          eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testStop() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getHeapMemoryUsageThreshold();

      doReturn(5L).when(configuration).getCheckIntervalInMillis();

      assertThat(subject.isStarted(), is(false));

      subject.stop();

      verifyNoMoreInteractions(scheduler);

      subject.start();

      assertThat(subject.isStarted(), is(true));

      subject.stop();

      assertThat(subject.isStarted(), is(false));
      verify(scheduler, times(1)).shutdownNow();
    }

    @Test
    public void testExceptionWhenEvaluatingConditions() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getHeapMemoryUsageThreshold();

      final RuntimeException toBeThrown = new RuntimeException();
      doThrow(toBeThrown).when(subject).runChecks();

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();
      verify(logger).error("An error occurred while running memory pools usage checks", toBeThrown);
    }

    @Test
    public void testErrorWhenEvaluatingConditions() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getHeapMemoryUsageThreshold();

      final Error toBeThrown = new OutOfMemoryError();
      doThrow(toBeThrown).when(subject).runChecks();

      subject.start();
      reset(logger);

      expectedException.expect(sameInstance(toBeThrown));

      try {
        scheduler.runNextPendingCommand();
      } finally {
        verify(logger).error("An error occurred while running memory pools usage checks",
            toBeThrown);
      }
    }

    @Test
    public void testHeapDumpSkippedBecauseOfFrequency() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getHeapMemoryUsageThreshold();
      doReturn(parse("1/1m")).when(configuration).getMaxFrequency();
      doReturn(1L).when(configuration).getCheckIntervalInMillis();
      doReturn(500L).when(heapMemoryUsage).getMax();
      doReturn(317L).when(heapMemoryUsage).getUsed();

      subject.start();

      reset(logger);

      scheduler.tick(1, TimeUnit.MILLISECONDS);

      verify(heapDumpCreator, times(1)).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'Heap' at 63.4% usage, "
              + "configured threshold is 20%")));

      for (int i = 0; i < 5; ++i) {
        scheduler.tick(1, TimeUnit.MILLISECONDS);
      }

      verify(heapDumpCreator, times(1)).createHeapDump(any(Date.class));
      verify(logger, times(5))
          .warning("Cannot create heap dump due to maximum frequency restrictions");
    }

    @Test
    public void testHeapAbsoluteConditionTriggersDumpLargerThan() throws Exception {
      doReturn(absoluteThresholdConfiguration(">20B")).when(configuration)
          .getHeapMemoryUsageThreshold();
      doReturn(500L).when(heapMemoryUsage).getMax();
      doReturn(317L).when(heapMemoryUsage).getUsed();

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'Heap' at 317B usage, "
              + "configured threshold is larger than 20B")));
    }

    @Test
    public void testHeapAbsoluteConditionTriggersDumpExactMatch() throws Exception {
      doReturn(absoluteThresholdConfiguration("==20B")).when(configuration)
          .getHeapMemoryUsageThreshold();
      doReturn(500L).when(heapMemoryUsage).getMax();
      doReturn(20L).when(heapMemoryUsage).getUsed();

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'Heap' at 20B usage, "
              + "configured threshold is equal to 20B")));
    }

    @Test
    public void testHeapAbsoluteConditionTriggersDumpSmallerThan() throws Exception {
      doReturn(absoluteThresholdConfiguration("<20B")).when(configuration)
          .getHeapMemoryUsageThreshold();
      doReturn(500L).when(heapMemoryUsage).getMax();
      doReturn(10L).when(heapMemoryUsage).getUsed();

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'Heap' at 10B usage, "
              + "configured threshold is smaller than 20B")));
    }

    @Test
    public void testHeapAbsoluteConditionDoesNotTriggerDump() throws Exception {
      doReturn(absoluteThresholdConfiguration("<10B")).when(configuration)
          .getHeapMemoryUsageThreshold();
      doReturn(100L).when(heapMemoryUsage).getMax();
      doReturn(50L).when(heapMemoryUsage).getUsed();

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testHeapDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getHeapMemoryUsageThreshold();
      doReturn(50L * 1024 * 1024).when(heapMemoryUsage).getMax();
      doReturn(1L).when(heapMemoryUsage).getUsed();

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testHeapThresholdNotConfigured() throws Exception {
      doReturn(50L).when(heapMemoryUsage).getMax();
      doReturn(50L).when(heapMemoryUsage).getUsed();

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

    @Test
    public void testCodeCacheTriggersDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getCodeCacheMemoryUsageThreshold();
      addMemoryPoolBean("Code Cache", 50L, 30L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'Code Cache' at 60% usage, configured "
              + "threshold is 20%")));
    }

    @Test
    public void testCodeCacheDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getCodeCacheMemoryUsageThreshold();
      addMemoryPoolBean("Code Cache", 50L, 1L);

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testCodeCacheThresholdNotConfigured() throws Exception {
      addMemoryPoolBean("Code Cache", 50L, 50L);

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

    @Test
    public void testEdenSpaceTriggersDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getEdenSpaceMemoryUsageThreshold();
      addMemoryPoolBean("PS Eden Space", 50L, 30L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'PS Eden Space' at 60% usage, "
              + "configured threshold is 20%")));
    }

    @Test
    public void testEdenSpaceDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getEdenSpaceMemoryUsageThreshold();
      addMemoryPoolBean("PS Eden Space", 50L, 1L);

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testEdenSpaceThresholdNotConfigured() throws Exception {
      addMemoryPoolBean("PS Eden Space", 50L, 50L);

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

    @Test
    public void testEdenSpaceAbsoluteConditionTriggersDumpLargerThan() throws Exception {
      doReturn(absoluteThresholdConfiguration(">20B")).when(configuration)
          .getEdenSpaceMemoryUsageThreshold();
      addMemoryPoolBean("PS Eden Space", 500L, 317L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'PS Eden Space' at 317B usage, "
              + "configured threshold is larger than 20B")));
    }

    @Test
    public void testEdenSpaceAbsoluteConditionTriggersDumpExactMatch() throws Exception {
      doReturn(absoluteThresholdConfiguration("==20B")).when(configuration)
          .getEdenSpaceMemoryUsageThreshold();
      addMemoryPoolBean("PS Eden Space", 500L, 20L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'PS Eden Space' at 20B usage, "
              + "configured threshold is equal to 20B")));
    }

    @Test
    public void testEdenSpaceAbsoluteConditionTriggersDumpSmallerThan() throws Exception {
      doReturn(absoluteThresholdConfiguration("<20B")).when(configuration)
          .getEdenSpaceMemoryUsageThreshold();
      addMemoryPoolBean("PS Eden Space", 500L, 10L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'PS Eden Space' at 10B usage, "
              + "configured threshold is smaller than 20B")));
    }

    @Test
    public void testSurvivorSpaceTriggersDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getSurvivorSpaceMemoryUsageThreshold();
      addMemoryPoolBean("PS Survivor Space", 50L, 30L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'PS Survivor Space' at 60% usage, "
              + "configured threshold is 20%")));
    }

    @Test
    public void testSurvivorSpaceDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getSurvivorSpaceMemoryUsageThreshold();
      addMemoryPoolBean("PS Survivor Space", 50L, 1L);

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testSurvivorSpaceThresholdNotConfigured() throws Exception {
      addMemoryPoolBean("PS Survivor Space", 50L, 50L);

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

    @Test
    public void testOldGenSpaceTriggersDump() throws Exception {
      doReturn(percentageThresholdConfiguration(20d)).when(configuration)
          .getOldGenSpaceMemoryUsageThreshold();
      addMemoryPoolBean("PS Old Gen", 50L, 30L);

      subject.start();
      reset(logger);

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator).createHeapDump(any(Date.class));
      verify(logger).info(argThat(Matchers.CharSequenceMatchers.equalTo(
          "Heap dump triggered because:\n* Memory pool 'PS Old Gen' at 60% usage, "
              + "configured threshold is 20%")));
    }

    @Test
    public void testOldGenSpaceDoesNotTriggerDump() throws Exception {
      doReturn(percentageThresholdConfiguration(MemoryPool.Type.OLD_GEN, 20d))
          .when(configuration).getOldGenSpaceMemoryUsageThreshold();
      addMemoryPoolBean("PS Old Gen", 50L, 1L);

      subject.start();

      scheduler.runNextPendingCommand();

      verify(heapDumpCreator, never()).createHeapDump(any(Date.class));
    }

    @Test
    public void testOldGenSpaceThresholdNotConfigured() throws Exception {
      addMemoryPoolBean("PS Old Gen", 50L, 50L);

      subject.start();

      verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
          any(TimeUnit.class));
    }

    /*
     * This test checks that the BigDecimal-based math does not explode when the calculation of the
     * memory usage ratio results in a number with infinite decimal positions.
     */
    @Test
    public void testInfiniteNumbersOfDecimals() throws Exception {
      final MemoryPool memoryPool = mock(MemoryPool.class);
      final MemoryUsage memoryUsage = mock(MemoryUsage.class);
      doReturn(memoryUsage).when(memoryPool).getMemoryUsage();
      doReturn(1131L).when(memoryUsage).getMax();
      doReturn(365L).when(memoryUsage).getUsed();

      new PercentageUsageThresholdCondition(new PercentageUsageThresholdConfiguration(
          MemoryPool.Type.HEAP, 50f), memoryPool).evaluate();
    }

    void addMemoryPoolBean(final String poolName, final long max, final long usage) {
      final MemoryUsage memoryUsage = mock(MemoryUsage.class);
      doReturn(max).when(memoryUsage).getMax();
      doReturn(usage).when(memoryUsage).getUsed();

      final MemoryPoolMXBean bean = mock(MemoryPoolMXBean.class);
      doReturn(poolName).when(bean).getName();
      doReturn(memoryUsage).when(bean).getUsage();
      memoryPoolBeans.add(bean);
    }

  }

}
