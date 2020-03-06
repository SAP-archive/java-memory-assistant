/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import com.sap.jma.utils.Supplier;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

public interface JavaVirtualMachine {

  MemoryPool getMemoryPool(MemoryPoolMXBean memoryPoolBean);

  MemoryPool getHeapMemoryPool(final MemoryMXBean memoryBean);

  enum Supported implements JavaVirtualMachine {

    ORACLE_7_X("Oracle Corporation", "1.7", // This also works for OpenJDK 7.x
        MemoryPool.Type.EDEN_SPACE, //
        MemoryPool.Type.SURVIVOR_SPACE, //
        MemoryPool.Type.OLD_GEN, //
        MemoryPool.Type.CODE_CACHE, //
        MemoryPool.Type.PERM_GEN),

    ORACLE_8_X("Oracle Corporation", "1.8", // This also works for OpenJDK 8.x
        MemoryPool.Type.EDEN_SPACE, //
        MemoryPool.Type.SURVIVOR_SPACE, //
        MemoryPool.Type.OLD_GEN, //
        MemoryPool.Type.CODE_CACHE, //
        MemoryPool.Type.COMPRESSED_CLASS_SPACE, //
        MemoryPool.Type.METASPACE),

    ORACLE_11_X("Oracle Corporation", "11", // This also works for OpenJDK 8.x
        MemoryPool.Type.EDEN_SPACE, //
        MemoryPool.Type.SURVIVOR_SPACE, //
        MemoryPool.Type.OLD_GEN, //
        MemoryPool.Type.CODE_CACHE, //
        MemoryPool.Type.COMPRESSED_CLASS_SPACE, //
        MemoryPool.Type.CODE_HEAP_NON_NMETHODS, //
        MemoryPool.Type.CODE_HEAP_NON_PROFILED_NMETHODS, //
        MemoryPool.Type.CODE_HEAP_PROFILED_NMETHODS, //
        MemoryPool.Type.METASPACE),

    ADOPTOPENJDK_HOTSPOT_8_X("AdoptOpenJDK", "1.8", // This also works for OpenJDK 8.x
        MemoryPool.Type.EDEN_SPACE, //
        MemoryPool.Type.SURVIVOR_SPACE, //
        MemoryPool.Type.OLD_GEN, //
        MemoryPool.Type.CODE_CACHE, //
        MemoryPool.Type.COMPRESSED_CLASS_SPACE, //
        MemoryPool.Type.METASPACE),

    ADOPTOPENJDK_HOTSPOT_11_X("AdoptOpenJDK", "11", // This also works for OpenJDK 8.x
        MemoryPool.Type.EDEN_SPACE, //
        MemoryPool.Type.SURVIVOR_SPACE, //
        MemoryPool.Type.OLD_GEN, //
        MemoryPool.Type.CODE_CACHE, //
        MemoryPool.Type.COMPRESSED_CLASS_SPACE, //
        MemoryPool.Type.CODE_HEAP_NON_NMETHODS, //
        MemoryPool.Type.CODE_HEAP_NON_PROFILED_NMETHODS, //
        MemoryPool.Type.CODE_HEAP_PROFILED_NMETHODS, //
        MemoryPool.Type.METASPACE),

    PIVOTAL_JDM_8_X("Pivotal Software Inc", "1.8",
        MemoryPool.Type.EDEN_SPACE, //
        MemoryPool.Type.SURVIVOR_SPACE, //
        MemoryPool.Type.TENURED_GEN, //
        MemoryPool.Type.CODE_CACHE, //
        MemoryPool.Type.COMPRESSED_CLASS_SPACE, //
        MemoryPool.Type.METASPACE),

    SAP_JVM_7_X("SAP AG", "1.7", //
        MemoryPool.Type.EDEN_SPACE, //
        MemoryPool.Type.SURVIVOR_SPACE, //
        MemoryPool.Type.OLD_GEN, //
        MemoryPool.Type.CODE_CACHE, //
        MemoryPool.Type.COMPRESSED_CLASS_SPACE, //
        MemoryPool.Type.METASPACE),

    SAP_JVM_8_X("SAP AG", "1.8", //
        MemoryPool.Type.EDEN_SPACE, //
        MemoryPool.Type.SURVIVOR_SPACE, //
        MemoryPool.Type.OLD_GEN, //
        MemoryPool.Type.CODE_CACHE, //
        MemoryPool.Type.COMPRESSED_CLASS_SPACE, //
        MemoryPool.Type.METASPACE),

    SAP_MACHINE_11_X("SAP SE", "11", //
        MemoryPool.Type.EDEN_SPACE, //
        MemoryPool.Type.SURVIVOR_SPACE, //
        MemoryPool.Type.OLD_GEN, //
        MemoryPool.Type.CODE_CACHE, //
        MemoryPool.Type.COMPRESSED_CLASS_SPACE, //
        MemoryPool.Type.CODE_HEAP_NON_NMETHODS, //
        MemoryPool.Type.CODE_HEAP_NON_PROFILED_NMETHODS, //
        MemoryPool.Type.CODE_HEAP_PROFILED_NMETHODS, //
        MemoryPool.Type.METASPACE);

    private final String vendor;

    private final String specVersion;

    private final List<MemoryPool.Type> supportedMemoryPoolTypes;

    Supported(final String vendor, final String specVersion,
              final MemoryPool.Type... supportedMemoryPoolTypes) {
      this.vendor = vendor;
      this.specVersion = specVersion;
      this.supportedMemoryPoolTypes = unmodifiableList(asList(supportedMemoryPoolTypes));
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

    public List<MemoryPool.Type> getSupportedMemoryPoolTypes() {
      return supportedMemoryPoolTypes;
    }

    @Override
    public MemoryPool getHeapMemoryPool(final MemoryMXBean memoryBean) {
      return new MemoryPoolImpl(MemoryPool.Type.HEAP, new Supplier<MemoryUsage>() {
        @Override
        public MemoryUsage get() {
          return memoryBean.getHeapMemoryUsage();
        }
      });
    }

    @Override
    public MemoryPool getMemoryPool(final MemoryPoolMXBean memoryPoolBean) {
      final MemoryPool.Type type = MemoryPool.Type.from(memoryPoolBean);

      boolean found = false;
      for (final MemoryPool.Type supportedType : getSupportedMemoryPoolTypes()) {
        if (type == supportedType) {
          found = true;
          break;
        }
      }

      if (!found) {
        throw new IllegalStateException(
            String.format("The memory pool type '%s' is not supported on the JVM type '%s'",
                type, this));
      }

      return new MemoryPoolImpl(type, new Supplier<MemoryUsage>() {
        @Override
        public MemoryUsage get() {
          return memoryPoolBean.getUsage();
        }
      });
    }

  }

  final class UnsupportedJavaVirtualMachineException extends Exception {
    public UnsupportedJavaVirtualMachineException(String vendor, String specVersion) {
      super(String.format("JVM with vendor '%s' and spec version '%s' is not supported",
          vendor, specVersion));
    }
  }

}
