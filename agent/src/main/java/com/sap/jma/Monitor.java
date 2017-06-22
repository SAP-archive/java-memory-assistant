/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import com.sap.jma.configuration.Configuration;
import com.sap.jma.logging.Logger;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class Monitor {

  protected final Logger logger;

  private final AtomicBoolean started = new AtomicBoolean(false);

  private final HeapDumpCreator heapDumpCreator;
  private final Configuration configuration;
  private final List<Date> executionHistory;

  Monitor(final HeapDumpCreator heapDumpCreator,
          final Configuration configuration, final Logger logger) {
    this.logger = logger;

    this.heapDumpCreator = heapDumpCreator;
    this.configuration = configuration;
    this.executionHistory = new LinkedList<>();
  }

  protected Configuration getConfiguration() {
    return configuration;
  }

  synchronized boolean triggerHeapDump() throws Exception {
    final boolean isFrequencyEnabled = configuration.getMaxFrequency() != null;

    final Date now = getCurrentDate();
    if (isFrequencyEnabled && !configuration.getMaxFrequency()
        .canPerformExecution(executionHistory, now)) {
      return false;
    }

    heapDumpCreator.createHeapDump(now);

    if (isFrequencyEnabled) {
      executionHistory.add(now);
      configuration.getMaxFrequency().filterToRelevantEntries(executionHistory, now);
    }

    return true;
  }

  // VisibleForTesting
  Date getCurrentDate() {
    return new Date();
  }

  public final boolean isStarted() {
    return started.get();
  }

  public final void start() throws Exception {
    if (!started.compareAndSet(false, true)) {
      return;
    }

    initialize();
  }

  protected abstract void initialize() throws Exception;

  public final void stop() throws Exception {
    if (!started.compareAndSet(true, false)) {
      return;
    }

    shutdown();
  }

  protected abstract void shutdown() throws Exception;

}
