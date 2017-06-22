/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import com.sap.jma.configuration.AbsoluteUsageThresholdConfiguration;
import com.sap.jma.configuration.Configuration;
import com.sap.jma.configuration.IncreaseOverTimeFrameUsageThresholdConfiguration;
import com.sap.jma.configuration.PercentageUsageThresholdConfiguration;
import com.sap.jma.configuration.UsageThresholdConfiguration;
import java.lang.management.MemoryPoolMXBean;

abstract class MemoryPoolImpl implements JavaVirtualMachine.MemoryPool {

  private final String literal;

  private MemoryPoolImpl(final String literal) {
    this.literal = literal;
  }

  static JavaVirtualMachine.MemoryPool from(final JavaVirtualMachine.MemoryPoolType type) {
    return new MemoryPoolImpl(type.getDefaultname()) {
      @Override
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return type.getThreshold(configuration);
      }
    };
  }

  public boolean matches(MemoryPoolMXBean memoryPoolBean) {
    return literal.equals(memoryPoolBean.getName());
  }

  public final UsageThresholdCondition getUsageCondition(
      final MemoryPoolMXBean memoryPoolBean, final Configuration configuration) {
    final UsageThresholdConfiguration usageThresholdConfiguration = getThreshold(configuration);

    if (usageThresholdConfiguration == null) {
      return null;
    } else if (usageThresholdConfiguration instanceof AbsoluteUsageThresholdConfiguration) {
      return new AbsoluteUsageThresholdConditionImpl() {

        @Override
        protected String getMemoryPoolName() {
          return memoryPoolBean.getName();
        }

        @Override
        protected double getCurrentUsageInBytes() {
          return memoryPoolBean.getUsage().getUsed();
        }

        @Override
        public AbsoluteUsageThresholdConfiguration getUsageThresholdCondition() {
          return (AbsoluteUsageThresholdConfiguration) usageThresholdConfiguration;
        }

      };
    } else if (usageThresholdConfiguration instanceof PercentageUsageThresholdConfiguration) {
      return new PercentageUsageThresholdConditionImpl() {

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
        public PercentageUsageThresholdConfiguration getUsageThresholdCondition() {
          return (PercentageUsageThresholdConfiguration) usageThresholdConfiguration;
        }

      };
    } else if (usageThresholdConfiguration instanceof IncreaseOverTimeFrameUsageThresholdConfiguration) {
      return new IncreaseOverTimeFrameUsageThresholdConditionImpl() {

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
        public IncreaseOverTimeFrameUsageThresholdConfiguration getUsageThresholdCondition() {
          return (IncreaseOverTimeFrameUsageThresholdConfiguration) usageThresholdConfiguration;
        }
      };
    } else {
      throw new IllegalStateException("Unknown type of threshold configuration: "
          + usageThresholdConfiguration);
    }
  }

  protected abstract UsageThresholdConfiguration getThreshold(Configuration configuration);

}