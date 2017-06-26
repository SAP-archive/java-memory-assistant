/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.conditions;

import com.sap.jma.configuration.AbsoluteUsageThresholdConfiguration;
import com.sap.jma.configuration.Comparison;
import com.sap.jma.configuration.MemorySizeUnit;
import com.sap.jma.logging.Logger;
import com.sap.jma.vms.MemoryPool;

public class AbsoluteUsageThresholdCondition extends
    AbstractUsageThresholdCondition<AbsoluteUsageThresholdConfiguration> {

  public AbsoluteUsageThresholdCondition(
      final AbsoluteUsageThresholdConfiguration configuration,
      final MemoryPool memoryPool) {
    this(configuration, memoryPool, Logger.Factory.get(AbsoluteUsageThresholdCondition.class));
  }

  //VisibleForTesting
  AbsoluteUsageThresholdCondition(
      final AbsoluteUsageThresholdConfiguration configuration,
      final MemoryPool memoryPool,
      final Logger logger) {
    super(configuration, memoryPool, logger);
  }

  public final void evaluate() throws UsageThresholdConditionViolatedException {
    final AbsoluteUsageThresholdConfiguration usageThreshold = getUsageThresholdConfiguration();
    final double currentUsageInBytes = memoryPool.getMemoryUsage().getUsed();
    final double targetUsageInBytes = usageThreshold.getTargetValueInBytes();
    final MemorySizeUnit memorySizeUnit = usageThreshold.getMemorySizeUnit();
    final Comparison comparison = usageThreshold.getComparison();

    if (comparison.compare(currentUsageInBytes, targetUsageInBytes)) {
      throw new UsageThresholdConditionViolatedException(
          getDescription(currentUsageInBytes, targetUsageInBytes, memorySizeUnit, comparison));
    }
  }

  private String getDescription(final double actualUsage,
                                final double targetUsage,
                                final MemorySizeUnit memorySize,
                                final Comparison comparisonOperator) {
    final String comparisonHumanReadable;
    switch (comparisonOperator) {
      case SMALLER_THAN:
        comparisonHumanReadable = "smaller than";
        break;
      case SMALLER_THAN_OR_EQUAL_TO:
        comparisonHumanReadable = "smaller than or equal to";
        break;
      case EQUAL_TO:
        comparisonHumanReadable = "equal to";
        break;
      case LARGER_THAN_OR_EQUAL_TO:
        comparisonHumanReadable = "larger than or equal to";
        break;
      case LARGER_THAN:
        comparisonHumanReadable = "larger than";
        break;
      default:
        throw new IllegalStateException();
    }

    return String.format("Memory pool '%s' at %s%s usage, configured threshold is %s %s%s",
        getMemoryPoolName(), DECIMAL_FORMAT.format(actualUsage), memorySize.getLiteral(),
        comparisonHumanReadable, DECIMAL_FORMAT.format(targetUsage), memorySize.getLiteral());
  }

  @Override
  public String toString() {
    final AbsoluteUsageThresholdConfiguration usageThreshold = getUsageThresholdConfiguration();

    final Comparison comparison = usageThreshold.getComparison();
    final String comparisonHumanReadable = toHumanReadable(comparison);

    return String.format("Memory pool '%s' used %s %s%s", getMemoryPoolName(),
        comparisonHumanReadable, DECIMAL_FORMAT.format(usageThreshold.getTargetValueInBytes()),
        usageThreshold.getMemorySizeUnit().getLiteral());
  }

  private static String toHumanReadable(Comparison comparison) {
    switch (comparison) {
      case SMALLER_THAN:
        return "smaller than";
      case SMALLER_THAN_OR_EQUAL_TO:
        return "smaller than or equal to";
      case EQUAL_TO:
        return "equal to";
      case LARGER_THAN_OR_EQUAL_TO:
        return "larger than or equal to";
      case LARGER_THAN:
        return "larger than";
      default:
        throw new IllegalStateException();
    }
  }

}
