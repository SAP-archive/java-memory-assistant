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

  enum NameMatcher {
    EQUALS {
      boolean match(final String defaultName, final String actualName) {
        return actualName.equals(defaultName);
      }
    },

    ENDS_WITH {
      boolean match(final String defaultName, final String actualName) {
        return actualName.endsWith(defaultName);
      }
    };

    abstract boolean match(final String defaultName, final String actualName);
  }

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

    EDEN_SPACE("Eden Space", NameMatcher.ENDS_WITH) {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getEdenSpaceMemoryUsageThreshold();
      }
    },

    SURVIVOR_SPACE("Survivor Space", NameMatcher.ENDS_WITH) {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getSurvivorSpaceMemoryUsageThreshold();
      }
    },

    OLD_GEN("Old Gen", NameMatcher.ENDS_WITH) {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getOldGenSpaceMemoryUsageThreshold();
      }
    },

    TENURED_GEN("Tenured Gen", NameMatcher.ENDS_WITH) {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getTenuredGenSpaceMemoryUsageThreshold();
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

    CODE_HEAP_NON_NMETHODS("CodeHeap 'non-nmethods'") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getCodeHeapNonNMethodsMemoryUsageThreshold();
      }
    },

    CODE_HEAP_PROFILED_NMETHODS("CodeHeap 'profiled nmethods'") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getCodeHeapProfiledNMethodsMemoryUsageThreshold();
      }
    },

    CODE_HEAP_NON_PROFILED_NMETHODS("CodeHeap 'non-profiled nmethods'") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getCodeHeapNonProfiledNMethodsMemoryUsageThreshold();
      }
    },

    METASPACE("Metaspace") {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getMetaspaceMemoryUsageThreshold();
      }
    },

    PERM_GEN("Perm Gen", NameMatcher.ENDS_WITH) {
      public UsageThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getPermGenMemoryUsageThreshold();
      }
    };

    private final String defaultName;
    private final NameMatcher nameMatcher;

    Type(final String defaultName) {
      this(defaultName, NameMatcher.EQUALS);
    }

    Type(final String defaultName, final NameMatcher nameMatcher) {
      this.defaultName = defaultName;
      this.nameMatcher = nameMatcher;
    }

    public String getDefaultName() {
      return defaultName;
    }

    public abstract UsageThresholdConfiguration getThreshold(Configuration configuration);

    static Type from(final MemoryPoolMXBean memoryPoolBean) {
      for (final Type type : values()) {
        if (type.nameMatcher.match(type.getDefaultName(), memoryPoolBean.getName())) {
          return type;
        }
      }

      throw new IllegalArgumentException();
    }

  }
}
