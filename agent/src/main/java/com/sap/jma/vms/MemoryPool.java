/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import com.sap.jma.conditions.UsageThresholdCondition;
import com.sap.jma.configuration.Configuration;
import com.sap.jma.configuration.UsageThresholdConfiguration;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

public interface MemoryPool {

  Type getType();

  UsageThresholdCondition<?> toCondition(Configuration configuration);

  boolean matches(MemoryPoolMXBean memoryPoolBean);

  String getName();

  MemoryUsage getMemoryUsage();

  /*
   * List of known memory pools found in the various versions of the JVMs with
   * mapping to which configuration entries we can use to set thresholds
   */
  enum Type {

    HEAP("Heap") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getHeapMemoryUsageThreshold();
      }
    },

    EDEN_SPACE("PS Eden Space") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getEdenSpaceMemoryUsageThreshold();
      }
    },

    SURVIVOR_SPACE("PS Survivor Space") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getSurvivorSpaceMemoryUsageThreshold();
      }
    },

    OLD_GEN("PS Old Gen") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getOldGenSpaceMemoryUsageThreshold();
      }
    },

    COMPRESSED_CLASS_SPACE("Compressed Class Space") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getCompressedClassSpaceMemoryUsageThreshold();
      }
    },

    CODE_CACHE("Code Cache") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getCodeCacheMemoryUsageThreshold();
      }
    },

    METASPACE("Metaspace") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getMetaspaceMemoryUsageThreshold();
      }
    },

    PERM_GEN("PS Perm Gen") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getPermGenMemoryUsageThreshold();
      }
    };

    private final String defaultName;

    Type(final String defaultName) {
      this.defaultName = defaultName;
    }

    public String getDefaultName() {
      return defaultName;
    }

    public abstract UsageThresholdConfiguration getThreshold(Configuration configuration);

    static Type from(final MemoryPoolMXBean memoryPoolBean) {
      for (final Type type : values()) {
        if (type.getDefaultName().equals(memoryPoolBean.getName())) {
          return type;
        }
      }

      throw new IllegalArgumentException("No memory pool type found to match memory pool bean: "
          + memoryPoolBean);
    }

  }
}
