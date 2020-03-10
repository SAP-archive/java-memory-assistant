/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import com.sap.jma.logging.Logger;
import com.sap.jma.utils.Supplier;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface JavaVirtualMachine {

  class Factory {

    public static final Factory INSTANCE = new Factory();

    public JavaVirtualMachine get(final Logger logger) {
      final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
      final String specVendor = runtimeBean.getSpecVendor();
      final String specVersion = runtimeBean.getSpecVersion();
      final String vmVendor = runtimeBean.getVmVendor();
      final String vmVersion = runtimeBean.getVmVersion();

      final MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
      final MemoryPool heapMemoryPool = new MemoryPoolImpl(MemoryPool.Type.HEAP,
          new Supplier<MemoryUsage>() {
            @Override
            public MemoryUsage get() {
              return memoryMxBean.getHeapMemoryUsage();
            }
          });

      final List<MemoryPoolMXBean> memoryPoolMxBeans = ManagementFactory.getMemoryPoolMXBeans();
      final List<MemoryPool> supportedMemoryPools = new ArrayList<>();
      for (final MemoryPoolMXBean memoryPoolBean : memoryPoolMxBeans) {
        try {
          final MemoryPool.Type type = MemoryPool.Type.from(memoryPoolBean);
          final MemoryPool memoryPool = new MemoryPoolImpl(type, new Supplier<MemoryUsage>() {
            @Override
            public MemoryUsage get() {
              return memoryPoolBean.getUsage();
            }
          });
          supportedMemoryPools.add(memoryPool);
        } catch (final IllegalArgumentException ex) {
          logger.error(ex.getMessage());
        }
      }

      final StringBuilder sb = new StringBuilder();
      for (final MemoryPool memoryPool : supportedMemoryPools) {
        sb.append('\n');
        sb.append(" * ");
        sb.append(memoryPool.getName());
      }

      logger.debug("JVM spec vendor: '%s'; spec version: '%s'; vm vendor: '%s'; vm version: "
                      + "'%s'; supported memory pools:%s",
              specVendor, specVersion, vmVendor, vmVersion, sb.toString());

      final List<MemoryPool> immutableMemoryPools =
          Collections.unmodifiableList(supportedMemoryPools);

      return new JavaVirtualMachine() {

        @Override
        public List<MemoryPool> getMemoryPools() {
          return immutableMemoryPools;
        }

        @Override
        public MemoryPool getHeapMemoryPool() {
          return heapMemoryPool;
        }

      };
    }

  }

  List<MemoryPool> getMemoryPools();

  MemoryPool getHeapMemoryPool();

}
