/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import com.sap.jma.configuration.Configuration;
import com.sap.jma.logging.Logger;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

@SuppressWarnings("nls")
class HeapDumpCreator {

  private final Configuration configuration;
  private final CommandExecutor commandExecutor;
  private final HeapDumpNameFormatter nameFormatter;
  private final PlatformManagedObject heapDumpBean;
  private final Method heapDumpMethod;
  private final Logger logger;

  HeapDumpCreator(final Configuration configuration, final CommandExecutor commandExecutor)
      throws Exception {
    this(configuration, initHostName(), ManagementFactory
        .getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class),
        commandExecutor, Logger.Factory.get(HeapDumpCreator.class));
  }

  // VisibleForTesting
  HeapDumpCreator(final Configuration configuration, final String hostName,
                  final PlatformManagedObject heapDumpBean, final CommandExecutor commandExecutor,
                  final Logger logger) throws Exception {
    this.configuration = configuration;
    this.nameFormatter = new HeapDumpNameFormatter(configuration.getHeapDumpName(), hostName);
    this.heapDumpBean = heapDumpBean;
    this.heapDumpMethod =
        heapDumpBean.getClass().getMethod("dumpHeap", String.class, boolean.class);
    this.commandExecutor = commandExecutor;
    this.logger = logger;
  }

  private static String initHostName() throws UnknownHostException {
    return InetAddress.getLocalHost().getHostName();
  }

  synchronized void createHeapDump(final Date timestamp) {
    final File heapDumpFolder = configuration.getHeapDumpFolder();

    final String heapDumpFileName =
        new File(heapDumpFolder, getHeapDumpFilename(timestamp)).getAbsolutePath();
    try {
      commandExecutor.executeBeforeHeapDumpCommand(heapDumpFileName);
    } catch (final CommandExecutor.CommandExecutionException ex) {
      logger.error(String.format("Execution of command before heap dump '%s' failed",
          heapDumpFileName), ex);
      return;
    }

    try {
      heapDumpMethod.invoke(heapDumpBean, heapDumpFileName, true);
      logger.info("Heap dump " + heapDumpFileName + " created");
    } catch (final Exception ex) {
      logger.error(String.format("An error occurred while dumping the heap to file '%s'",
          heapDumpFileName), ex);
      return;
    }

    try {
      commandExecutor.executeAfterHeapDumpCommand(heapDumpFileName);
    } catch (final CommandExecutor.CommandExecutionException ex) {
      logger.error(String.format("Execution of command after heap dump '%s' failed",
          heapDumpFileName), ex);
    }
  }

  private String getHeapDumpFilename(final Date timestamp) {
    return nameFormatter.format(timestamp);
  }

}
