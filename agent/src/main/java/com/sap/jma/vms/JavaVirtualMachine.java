/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import com.sap.jma.configuration.Configuration;
import com.sap.jma.configuration.ThresholdConfiguration;
import java.lang.management.MemoryPoolMXBean;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

public interface JavaVirtualMachine {

  MemoryPool findMemoryPool(final MemoryPoolMXBean memoryPoolBean);

  enum Supported implements JavaVirtualMachine {

    ORACLE_7_X("Oracle Corporation", "1.7", // This also works for OpenJDK 7.x
        MemoryPoolImpl.from(MemoryPoolType.EDEN_SPACE), //
        MemoryPoolImpl.from(MemoryPoolType.SURVIVOR_SPACE), //
        MemoryPoolImpl.from(MemoryPoolType.OLD_GEN), //
        MemoryPoolImpl.from(MemoryPoolType.CODE_CACHE), //
        MemoryPoolImpl.from(MemoryPoolType.PERM_GEN)),

    ORACLE_8_X("Oracle Corporation", "1.8", // This also works for OpenJDK 8.x
        MemoryPoolImpl.from(MemoryPoolType.EDEN_SPACE), //
        MemoryPoolImpl.from(MemoryPoolType.SURVIVOR_SPACE), //
        MemoryPoolImpl.from(MemoryPoolType.OLD_GEN), //
        MemoryPoolImpl.from(MemoryPoolType.CODE_CACHE), //
        MemoryPoolImpl.from(MemoryPoolType.COMPRESSED_CLASS_SPACE), //
        MemoryPoolImpl.from(MemoryPoolType.METASPACE)),

    SAP_7_X("SAP AG", "1.7", //
        MemoryPoolImpl.from(MemoryPoolType.EDEN_SPACE), //
        MemoryPoolImpl.from(MemoryPoolType.SURVIVOR_SPACE), //
        MemoryPoolImpl.from(MemoryPoolType.OLD_GEN), //
        MemoryPoolImpl.from(MemoryPoolType.CODE_CACHE), //
        MemoryPoolImpl.from(MemoryPoolType.COMPRESSED_CLASS_SPACE), //
        MemoryPoolImpl.from(MemoryPoolType.METASPACE)),

    SAP_8_X("SAP AG", "1.8", //
        MemoryPoolImpl.from(MemoryPoolType.EDEN_SPACE), //
        MemoryPoolImpl.from(MemoryPoolType.SURVIVOR_SPACE), //
        MemoryPoolImpl.from(MemoryPoolType.OLD_GEN), //
        MemoryPoolImpl.from(MemoryPoolType.CODE_CACHE), //
        MemoryPoolImpl.from(MemoryPoolType.COMPRESSED_CLASS_SPACE), //
        MemoryPoolImpl.from(MemoryPoolType.METASPACE));

    private final String vendor;

    private final String specVersion;

    private final List<MemoryPool> memoryPools;

    Supported(final String vendor, final String specVersion, final MemoryPool... memoryPools) {
      this.vendor = vendor;
      this.specVersion = specVersion;
      this.memoryPools = Collections.unmodifiableList(Arrays.asList(memoryPools));
    }

    public static JavaVirtualMachine find(final String vendor, final String specVersion)
        throws UnsupportedJavaVirtualMachineException {
      for (final Supported jvm : Supported.values()) {
        if (jvm.vendor.equals(vendor) && jvm.specVersion.equals(specVersion)) {
          return jvm;
        }
      }

      throw new UnsupportedJavaVirtualMachineException(vendor, specVersion);
    }

    public MemoryPool findMemoryPool(final MemoryPoolMXBean memoryPoolBean) {
      for (final MemoryPool memoryPool : memoryPools) {
        if (memoryPool.matches(memoryPoolBean)) {
          return memoryPool;
        }
      }

      throw new NoSuchElementException(
          String.format("The memory pool '%s' is not known for the '%s' JVM",
          memoryPoolBean.getName(),
          name()));
    }

  }

  /*
   * List of known memory pools found in the various versions of the JVMs with
   * mapping to which configuration entries we can use to set thresholds
   */
  enum MemoryPoolType {

    HEAP("Heap") {
      public ThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getHeapMemoryUsageThreshold();
      }
    },

    EDEN_SPACE("PS Eden Space") {
      public ThresholdConfiguration getThreshold(final Configuration configuration) {
        return configuration.getEdenSpaceMemoryUsageThreshold();
      }
    },

    SURVIVOR_SPACE("PS Survivor Space") {
      public ThresholdConfiguration getThreshold(Configuration configuration) {
        return configuration.getSurvivorSpaceMemoryUsageThreshold();
      }
    },

    OLD_GEN("PS Old Gen") {
      public ThresholdConfiguration getThreshold(Configuration configuration) {
        return configuration.getOldGenSpaceMemoryUsageThreshold();
      }
    },

    COMPRESSED_CLASS_SPACE("Compressed Class Space") {
      public ThresholdConfiguration getThreshold(Configuration configuration) {
        return configuration.getCompressedClassSpaceMemoryUsageThreshold();
      }
    },

    CODE_CACHE("Code Cache") {
      public ThresholdConfiguration getThreshold(Configuration configuration) {
        return configuration.getCodeCacheMemoryUsageThreshold();
      }
    },

    METASPACE("Metaspace") {
      public ThresholdConfiguration getThreshold(Configuration configuration) {
        return configuration.getMetaspaceMemoryUsageThreshold();
      }
    },

    PERM_GEN("PS Perm Gen") {
      public ThresholdConfiguration getThreshold(Configuration configuration) {
        return configuration.getPermGenMemoryUsageThreshold();
      }
    };

    private final String defaultName;

    MemoryPoolType(final String defaultName) {
      this.defaultName = defaultName;
    }

    public String getDefaultname() {
      return defaultName;
    }

    abstract ThresholdConfiguration getThreshold(Configuration configuration);

  }

  interface MemoryPool {

    boolean matches(MemoryPoolMXBean memoryPoolBean);

    UsageThresholdCondition getUsageCondition(MemoryPoolMXBean memoryPoolBean,
                                              Configuration configuration);

  }

  interface UsageThresholdCondition<C extends ThresholdConfiguration> {

    C getUsageThreshold();

    void evaluate() throws UsageThresholdConditionViolatedException;

  }

  final class UsageThresholdConditionViolatedException extends Exception {
    UsageThresholdConditionViolatedException(String message) {
      super(message);
    }
  }

  final class UnsupportedJavaVirtualMachineException extends Exception {
    UnsupportedJavaVirtualMachineException(String vendor, String specVersion) {
      super(String.format("JVM with vendor '%s' and spec version '%s' is not supported",
          vendor, specVersion));
    }
  }

}
