/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import com.sap.jma.Configuration;
import com.sap.jma.Configuration.IncreaseOverTimeFrameThresholdConfiguration;
import java.lang.management.MemoryPoolMXBean;

abstract class MemoryPoolImpl implements JavaVirtualMachine.MemoryPool {

  private final String literal;

  private MemoryPoolImpl(final String literal) {
    this.literal = literal;
  }

  static JavaVirtualMachine.MemoryPool from(final JavaVirtualMachine.MemoryPoolType type) {
    return new MemoryPoolImpl(type.getDefaultname()) {
      @Override
      public Configuration.ThresholdConfiguration getThreshold(final Configuration configuration) {
        return type.getThreshold(configuration);
      }
    };
  }

  public boolean matches(MemoryPoolMXBean memoryPoolBean) {
    return literal.equals(memoryPoolBean.getName());
  }

  public final JavaVirtualMachine.UsageThresholdCondition getUsageCondition(
      final MemoryPoolMXBean memoryPoolBean, final Configuration configuration) {
    final Configuration.ThresholdConfiguration thresholdConfiguration = getThreshold(configuration);

    if (thresholdConfiguration == null) {
      return null;
    }

    if (thresholdConfiguration instanceof Configuration.PercentageThresholdConfiguration) {
      return new AbsoluteUsageThresholdConditionImpl() {

        @Override
        protected String getMemoryPoolName() {
          return memoryPoolBean.getName();
        }

        @Override
        protected long getMemoryUsed() {
          return memoryPoolBean.getUsage().getUsed();
        }

        @Override
        protected long getMemoryMax() {
          return memoryPoolBean.getUsage().getMax();
        }

        @Override
        public Configuration.PercentageThresholdConfiguration getUsageThreshold() {
          return (Configuration.PercentageThresholdConfiguration) thresholdConfiguration;
        }

      };
    } else if (thresholdConfiguration instanceof IncreaseOverTimeFrameThresholdConfiguration) {
      return new IncreaseOverTimeFrameThresholdConditionImpl() {

        @Override
        protected String getMemoryPoolName() {
          return memoryPoolBean.getName();
        }

        @Override
        protected long getMemoryUsed() {
          return memoryPoolBean.getUsage().getUsed();
        }

        @Override
        protected long getMemoryMax() {
          return memoryPoolBean.getUsage().getMax();
        }

        @Override
        public IncreaseOverTimeFrameThresholdConfiguration getUsageThreshold() {
          return (IncreaseOverTimeFrameThresholdConfiguration) thresholdConfiguration;
        }
      };
    } else {
      throw new IllegalStateException("Unknown type of threshold configuration: "
          + thresholdConfiguration);
    }
  }

  protected abstract Configuration.ThresholdConfiguration getThreshold(Configuration configuration);

}