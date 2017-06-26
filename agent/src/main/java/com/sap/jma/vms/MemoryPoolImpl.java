/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import com.sap.jma.conditions.UsageThresholdCondition;
import com.sap.jma.configuration.Configuration;
import com.sap.jma.configuration.UsageThresholdConfiguration;
import com.sap.jma.utils.Supplier;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

public class MemoryPoolImpl implements MemoryPool {

  private final Type type;
  private final Supplier<MemoryUsage> memoryUsageSupplier;

  MemoryPoolImpl(final Type type,
                 final Supplier<MemoryUsage> memoryUsageSupplier) {
    this.type = type;
    this.memoryUsageSupplier = memoryUsageSupplier;
  }

  @Override
  public String getName() {
    return type.getDefaultName();
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public UsageThresholdCondition<?> toCondition(final Configuration configuration) {
    final UsageThresholdConfiguration usageThresholdConfiguration =
        type.getThreshold(configuration);

    if (usageThresholdConfiguration == null) {
      return null;
    }

    return usageThresholdConfiguration.toCondition(this);
  }

  @Override
  public MemoryUsage getMemoryUsage() {
    return memoryUsageSupplier.get();
  }

  public boolean matches(final MemoryPoolMXBean memoryPoolBean) {
    return type.getDefaultName().equals(memoryPoolBean.getName());
  }

}