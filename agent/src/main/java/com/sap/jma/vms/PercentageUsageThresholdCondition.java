/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import com.sap.jma.configuration.PercentageUsageThresholdConfiguration;
import com.sap.jma.logging.Logger;
import java.lang.management.MemoryUsage;

public class PercentageUsageThresholdCondition
    extends AbstractUsageThresholdCondition<PercentageUsageThresholdConfiguration> {

  private static final Logger LOGGER =
      Logger.Factory.get(UsageThresholdCondition.class);

  public PercentageUsageThresholdCondition(
      final PercentageUsageThresholdConfiguration configuration,
      final MemoryPool memoryPool) {
    this(configuration, memoryPool, Logger.Factory.get(PercentageUsageThresholdCondition.class));
  }

  // VisibleForTesting
  PercentageUsageThresholdCondition(
      final PercentageUsageThresholdConfiguration configuration,
      final MemoryPool memoryPool,
      final Logger logger) {
    super(configuration, memoryPool, logger);
  }

  private double getCurrentUsageRatio() {
    final MemoryUsage memoryUsage = memoryPool.getMemoryUsage();
    return memoryUsage.getUsed() * 100d / memoryUsage.getMax();
  }

  public final void evaluate() throws JavaVirtualMachine.UsageThresholdConditionViolatedException {
    final double usageRatio = getCurrentUsageRatio();

    if (getUsageThresholdConfiguration().getValue() < usageRatio) {
      throw new JavaVirtualMachine.UsageThresholdConditionViolatedException(
          getDescription(usageRatio));
    } else {
      LOGGER.debug(getDescription(usageRatio));
    }
  }

  private String getDescription(double usageRatio) {
    return String.format("Memory pool '%s' at %s%% usage, configured threshold is %s%%",
        getMemoryPoolName(), DECIMAL_FORMAT.format(usageRatio),
        DECIMAL_FORMAT.format(getUsageThresholdConfiguration().getValue()));
  }

  @Override
  public String toString() {
    return String.format("Memory pool '%s' used to %s%% or more", getMemoryPoolName(),
        DECIMAL_FORMAT.format(getUsageThresholdConfiguration().getValue()));
  }

}