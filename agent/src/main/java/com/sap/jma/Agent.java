/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import com.sap.jma.logging.Logger;
import java.lang.instrument.Instrumentation;

/**
 * Entry class for starting the HeapDumpAgent.
 */
public class Agent {

  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    initAgent(agentArgs, Logger.Factory.get(Agent.class));
  }

  @SuppressWarnings("checkstyle:unused")
  public static void agentmain(final String agentArgs,
                               final Instrumentation inst) throws Exception {
    initAgent(agentArgs, Logger.Factory.get(Agent.class));
  }

  // VisibleForTesting
  static void initAgent(String agentArgs, final Logger logger) {
    agentArgs = (agentArgs == null || agentArgs.trim().isEmpty()) ? null : agentArgs;

    if (agentArgs != null) {
      logger.warning(
          "HeapDumpAgent does not accept configurations through -javaagent;"
              + " use System properties instead");
    }

    final Agent agent = new Agent();

    try {
      agent.start(agentArgs == null || agentArgs.trim().isEmpty() ? null : agentArgs, logger);
    } catch (final Exception ex) {
      logger.error("HeapDumpAgent cannot start", ex);
    }
  }

  private void start(final String agentArgs, final Logger logger) throws Exception {
    if (agentArgs != null) {
      logger.warning(
          "HeapDumpAgent does not accept configurations "
              + "through -javaagent; use System properties instead");
    }

    final Configuration configuration =
        Configuration.Builder.initializeFromSystemProperties().build();

    Logger.Factory.initialize(configuration.getLogLevel());

    for (final String override : configuration.getOverrides()) {
      logger.debug(override);
    }

    final CommandExecutor commandExecutor = new CommandExecutor(configuration);

    final AbstractMonitor heapDumpMonitor =
        new MBeanMonitor(new HeapDumpCreator(configuration, commandExecutor), configuration);

    // Parallelize hooks to optimize exec time (all hooks are run in parallel by the JVM)
    registerShutDownHook(new Runnable() {
      @Override
      public void run() {
        try {
          heapDumpMonitor.shutdown();
        } catch (final Exception ex) {
          logger.error("Error while shutting down the HeapDump monitor", ex);
        }
      }
    });
    registerShutDownHook(new Runnable() {
      @Override
      public void run() {
        try {
          commandExecutor.executeOnShutdownCommand();
        } catch (final Exception ex) {
          logger.error("Error while executing the shutting down command", ex);
        }
      }
    });

    heapDumpMonitor.start();
  }

  private void registerShutDownHook(final Runnable runnable) {
    final Thread shutdownThread = new Thread(runnable);
    shutdownThread.setPriority(Thread.MAX_PRIORITY);
    shutdownThread.setName("HeapDumpAgent ShutdownHook");

    Runtime.getRuntime().addShutdownHook(shutdownThread);
  }

}
