/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import com.sap.jma.configuration.AbsoluteThresholdConfiguration;
import com.sap.jma.configuration.Configuration;
import com.sap.jma.configuration.IncreaseOverTimeFrameThresholdConfiguration;
import com.sap.jma.configuration.PercentageThresholdConfiguration;
import com.sap.jma.configuration.ThresholdConfiguration;
import java.lang.management.MemoryPoolMXBean;

abstract class MemoryPoolImpl implements JavaVirtualMachine.MemoryPool {

  private final String literal;

  private MemoryPoolImpl(final String literal) {
    this.literal = literal;
  }

  static JavaVirtualMachine.MemoryPool from(final JavaVirtualMachine.MemoryPoolType type) {
    return new MemoryPoolImpl(type.getDefaultname()) {
      @Override
      public ThresholdConfiguration getThreshold(final Configuration configuration) {
        return type.getThreshold(configuration);
      }
    };
  }

  public boolean matches(MemoryPoolMXBean memoryPoolBean) {
    return literal.equals(memoryPoolBean.getName());
  }

  public final JavaVirtualMachine.UsageThresholdCondition getUsageCondition(
      final MemoryPoolMXBean memoryPoolBean, final Configuration configuration) {
    final ThresholdConfiguration thresholdConfiguration = getThreshold(configuration);

    if (thresholdConfiguration == null) {
      return null;
    } else if (thresholdConfiguration instanceof AbsoluteThresholdConfiguration) {
      return new AbsoluteThresholdConditionImpl() {

        @Override
        protected String getMemoryPoolName() {
          return memoryPoolBean.getName();
        }

        @Override
        protected double getCurrentUsageInBytes() {
          return memoryPoolBean.getUsage().getUsed();
        }

        @Override
        public AbsoluteThresholdConfiguration getUsageThreshold() {
          return (AbsoluteThresholdConfiguration) thresholdConfiguration;
        }

      };
    } else if (thresholdConfiguration instanceof PercentageThresholdConfiguration) {
      return new PercentageThresholdConditionImpl() {

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
        public PercentageThresholdConfiguration getUsageThreshold() {
          return (PercentageThresholdConfiguration) thresholdConfiguration;
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

  protected abstract ThresholdConfiguration getThreshold(Configuration configuration);

}