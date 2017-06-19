/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import com.sap.jma.Configuration;
import com.sap.jma.logging.Logger;

public abstract class PercentageThresholdConditionImpl
    extends AbstractUsageThresholdConditionImpl<Configuration.PercentageThresholdConfiguration> {

  private static final Logger LOGGER =
      Logger.Factory.get(JavaVirtualMachine.UsageThresholdCondition.class);

  private final Configuration.PercentageThresholdConfiguration usageThreshold =
      getUsageThreshold();

  protected abstract long getMemoryUsed();

  protected abstract long getMemoryMax();

  private double getCurrentUsageRatio() {
    return getMemoryUsed() * 100d / getMemoryMax();
  }

  public final void evaluate() throws JavaVirtualMachine.UsageThresholdConditionViolatedException {
    final double usageRatio = getCurrentUsageRatio();

    if (usageThreshold.getValue() < usageRatio) {
      throw new JavaVirtualMachine.UsageThresholdConditionViolatedException(
          getDescription(usageRatio));
    } else {
      LOGGER.debug(getDescription(usageRatio));
    }
  }

  private String getDescription(double usageRatio) {
    return String.format("Memory pool '%s' at %s%% usage, configured threshold is %s%%",
        getMemoryPoolName(), DECIMAL_FORMAT.format(usageRatio),
        DECIMAL_FORMAT.format(usageThreshold.getValue()));
  }

  @Override
  public String toString() {
    return String.format("Memory pool '%s' used to %s%% or more", getMemoryPoolName(),
        DECIMAL_FORMAT.format(usageThreshold.getValue()));
  }

}