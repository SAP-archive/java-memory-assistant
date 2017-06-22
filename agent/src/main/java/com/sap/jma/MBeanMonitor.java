/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import static com.sap.jma.concurrent.ThreadFactories.deamons;

import com.sap.jma.configuration.AbsoluteThresholdConfiguration;
import com.sap.jma.configuration.Configuration;
import com.sap.jma.configuration.IncreaseOverTimeFrameThresholdConfiguration;
import com.sap.jma.configuration.PercentageThresholdConfiguration;
import com.sap.jma.configuration.ThresholdConfiguration;
import com.sap.jma.logging.Logger;
import com.sap.jma.vms.AbsoluteThresholdConditionImpl;
import com.sap.jma.vms.IncreaseOverTimeFrameThresholdConditionImpl;
import com.sap.jma.vms.JavaVirtualMachine;
import com.sap.jma.vms.PercentageThresholdConditionImpl;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class MBeanMonitor extends Monitor {

  private final List<JavaVirtualMachine.UsageThresholdCondition> memoryPoolsConditions =
      new ArrayList<>();

  private final Callable<ScheduledExecutorService> executorServiceProvider;

  private ScheduledExecutorService executorService;

  MBeanMonitor(final HeapDumpCreator heapDumpCreator, final Configuration configuration) {
    this(heapDumpCreator, configuration, new Callable<ScheduledExecutorService>() {
      @Override
      public ScheduledExecutorService call() throws Exception {
        return Executors.newSingleThreadScheduledExecutor(deamons("JavaMemoryAssistant"));
      }
    }, Logger.Factory.get(MBeanMonitor.class));
  }

  MBeanMonitor(final HeapDumpCreator heapDumpCreator, final Configuration configuration,
               final Callable<ScheduledExecutorService> executorServiceProvider,
               final Logger logger) {
    super(heapDumpCreator, configuration, logger);
    this.executorServiceProvider = executorServiceProvider;
  }

  @Override
  protected void initialize() throws Exception {
    final Configuration configuration = getConfiguration();

    final JavaVirtualMachine jvm = findCurrentJvm();

    final ThresholdConfiguration config = configuration.getHeapMemoryUsageThreshold();
    final JavaVirtualMachine.UsageThresholdCondition condition;
    if (config == null) {
      condition = null;
    } else if (config instanceof AbsoluteThresholdConfiguration) {
      final MemoryMXBean memoryBean = getMemoryMxBean();
      condition = new AbsoluteThresholdConditionImpl() {
        @Override
        protected String getMemoryPoolName() {
          return "Heap";
        }

        @Override
        protected double getCurrentUsageInBytes() {
          return memoryBean.getHeapMemoryUsage().getUsed();
        }

        @Override
        public AbsoluteThresholdConfiguration getUsageThreshold() {
          return (AbsoluteThresholdConfiguration) config;
        }
      };
    } else if (config instanceof IncreaseOverTimeFrameThresholdConfiguration) {
      final MemoryMXBean memoryBean = getMemoryMxBean();
      condition = new IncreaseOverTimeFrameThresholdConditionImpl() {
        @Override
        protected String getMemoryPoolName() {
          return "Heap";
        }

        @Override
        protected long getMemoryUsed() {
          return memoryBean.getHeapMemoryUsage().getUsed();
        }

        @Override
        protected long getMemoryMax() {
          return memoryBean.getHeapMemoryUsage().getMax();
        }

        @Override
        public IncreaseOverTimeFrameThresholdConfiguration getUsageThreshold() {
          return (IncreaseOverTimeFrameThresholdConfiguration) config;
        }
      };
    } else if (config instanceof PercentageThresholdConfiguration) {
      final MemoryMXBean memoryBean = getMemoryMxBean();
      condition = new PercentageThresholdConditionImpl() {
        @Override
        protected String getMemoryPoolName() {
          return "Heap";
        }

        @Override
        protected long getMemoryUsed() {
          return memoryBean.getHeapMemoryUsage().getUsed();
        }

        @Override
        protected long getMemoryMax() {
          return memoryBean.getHeapMemoryUsage().getMax();
        }

        @Override
        public PercentageThresholdConfiguration getUsageThreshold() {
          return (PercentageThresholdConfiguration) config;
        }

        @Override
        public String toString() {
          return configuration.getHeapMemoryUsageThreshold().toString();
        }
      };
    } else {
      throw new IllegalStateException("Unsupported threshold configuration type: "
          + configuration.getClass().getName());
    }

    if (condition != null) {
      memoryPoolsConditions.add(condition);
    }

    final List<MemoryPoolMXBean> memoryPoolMxBeans = getMemoryPoolMxBeans();
    for (final MemoryPoolMXBean poolBean : memoryPoolMxBeans) {
      final String poolName = poolBean.getName();
      logger.debug(String.format("Memory pool found: %s", poolName));

      final JavaVirtualMachine.MemoryPool memoryPool = jvm.findMemoryPool(poolBean);
      final JavaVirtualMachine.UsageThresholdCondition memoryPoolCondition =
          memoryPool.getUsageCondition(poolBean, configuration);

      if (memoryPoolCondition != null) {
        memoryPoolsConditions.add(memoryPoolCondition);
      }
    }

    if (memoryPoolsConditions.isEmpty()) {
      logger.warning("No memory conditions have been specified; "
          + "the heap-dump agent will not perform checks");
      return;
    }

    final String memoryConditionsMessage = (memoryPoolsConditions.size() == 1)
        ? "One memory condition has been specified"
        : String.format("%d memory conditions have been specified", memoryPoolsConditions.size());

    if (configuration.getCheckIntervalInMillis() < 0) {
      logger.error(memoryConditionsMessage
          + ", but no check interval has been provided; "
          + "the heap-dump agent will not perform checks");
    } else {
      final StringBuilder conditions = new StringBuilder();
      for (final JavaVirtualMachine.UsageThresholdCondition memoryPoolCondition :
          memoryPoolsConditions) {
        conditions.append('\n');
        conditions.append('*');
        conditions.append(' ');
        conditions.append(memoryPoolCondition);
      }

      logger.debug(memoryConditionsMessage
          + String.format(" (checks will occur every %d milliseconds):%s",
              configuration.getCheckIntervalInMillis(), conditions));

      executorService = executorServiceProvider.call();
      executorService.scheduleWithFixedDelay(new HeapDumpCheck(),
          getConfiguration().getCheckIntervalInMillis(),
          getConfiguration().getCheckIntervalInMillis(),
          TimeUnit.MILLISECONDS);
    }
  }

  List<MemoryPoolMXBean> getMemoryPoolMxBeans() {
    return ManagementFactory.getMemoryPoolMXBeans();
  }

  MemoryMXBean getMemoryMxBean() {
    return ManagementFactory.getMemoryMXBean();
  }

  @Override
  protected void shutdown() throws Exception {
    try {
      if (executorService != null) {
        executorService.shutdownNow();
      }
    } finally {
      executorService = null;
    }
  }

  // VisibleForTesting
  RuntimeMXBean getRuntimeMxBean() {
    return ManagementFactory.getRuntimeMXBean();
  }

  // VisibleForTesting
  JavaVirtualMachine findCurrentJvm()
      throws JavaVirtualMachine.UnsupportedJavaVirtualMachineException {
    final RuntimeMXBean runtimeBean = getRuntimeMxBean();
    final String specVendor = runtimeBean.getSpecVendor();
    final String specVersion = runtimeBean.getSpecVersion();
    final String vmVendor = runtimeBean.getVmVendor();
    final String vmVersion = runtimeBean.getVmVersion();

    logger.debug(
        String.format("JVM spec vendor: '%s'; spec version: '%s'; vm vendor: '%s'; "
            + "vm version: '%s'", specVendor, specVersion, vmVendor, vmVersion));

    return JavaVirtualMachine.Supported.find(vmVendor, specVersion);
  }

  // VisibleForTesting
  void runChecks() {
    final List<String> reasons = new LinkedList<>();
    for (final JavaVirtualMachine.UsageThresholdCondition condition : memoryPoolsConditions) {
      try {
        condition.evaluate();
      } catch (JavaVirtualMachine.UsageThresholdConditionViolatedException ex) {
        reasons.add(ex.getMessage());
      }
    }

    if (!reasons.isEmpty()) {
      final StringBuilder sb = new StringBuilder("Heap dump triggered because:");
      for (final String reason : reasons) {
        sb.append("\n* ");
        sb.append(reason);
      }

      boolean printCause = true;
      try {
        printCause = triggerHeapDump();
        if (!printCause) {
          logger.warning("Cannot create heap dump due to maximum frequency restrictions");
        }
      } catch (final Exception ex) {
        logger.error("Error while triggering heap dump", ex);
      } finally {
        if (printCause) {
          logger.info(sb.toString());
        }
      }
    }
  }

  private class HeapDumpCheck implements Runnable {

    @Override
    public void run() {
      try {
        logger.debug("Starting check of thresholds for configured memory pools");

        runChecks();

        logger.debug("Check of thresholds for configured memory pools done");
      } catch (final Throwable th) {
        logger.error("An error occurred while running memory pools usage checks", th);

        if (th instanceof Error) {
          throw (Error) th;
        }
      }
    }

  }

}
