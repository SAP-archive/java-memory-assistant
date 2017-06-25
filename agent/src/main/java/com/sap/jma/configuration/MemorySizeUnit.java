/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.configuration;

import com.sap.jma.utils.EnumUtils;

public enum MemorySizeUnit {

  GIGABYTE("GB", 1024 * 1024 * 1024d),

  MEGABYTE("MB", 1024 * 1024d),

  KILOBYTE("KB", 1024d),

  BYTE("B", 1d);

  private final String literal;
  private final double multiplierToBytes;

  MemorySizeUnit(final String literal, final double multiplierToBytes) {
    this.literal = literal;
    this.multiplierToBytes = multiplierToBytes;
  }

  public static MemorySizeUnit from(final String actual) {
    for (final MemorySizeUnit unit : values()) {
      if (unit.literal.equals(actual.trim())) {
        return unit;
      }
    }

    throw new IllegalArgumentException(
        String.format("Memory size unit '%s' is not recognized; valid values are: %s", actual,
            EnumUtils.join(values())));
  }

  public double toBytes(double valueInUnitSize) {
    return valueInUnitSize * multiplierToBytes;
  }

  public String getLiteral() {
    return literal;
  }

}
