/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.vms;

import com.sap.jma.Configuration.ThresholdConfiguration;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

abstract class AbstractUsageThresholdConditionImpl<C extends ThresholdConfiguration>
    implements JavaVirtualMachine.UsageThresholdCondition<C> {

  static final DecimalFormat DECIMAL_FORMAT =
      new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US));

  protected abstract String getMemoryPoolName();

  public abstract C getUsageThreshold();

}
