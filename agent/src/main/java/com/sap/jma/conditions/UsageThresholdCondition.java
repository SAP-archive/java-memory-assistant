/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.conditions;

import com.sap.jma.configuration.UsageThresholdConfiguration;

public interface UsageThresholdCondition<C extends UsageThresholdConfiguration> {

  C getUsageThresholdConfiguration();

  void evaluate() throws UsageThresholdConditionViolatedException;

  final class UsageThresholdConditionViolatedException extends Exception {
    UsageThresholdConditionViolatedException(String message) {
      super(message);
    }
  }

}