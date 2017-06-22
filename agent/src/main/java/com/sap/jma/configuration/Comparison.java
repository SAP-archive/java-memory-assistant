/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.configuration;

import com.sap.jma.utils.EnumUtils;

public enum Comparison {
  SMALLER_THAN("<") {
    @Override
    public boolean compare(double expected, double actual) {
      return expected < actual;
    }
  },
  SMALLER_THAN_OR_EQUAL_TO("<=") {
    @Override
    public boolean compare(double expected, double actual) {
      return expected <= actual;
    }
  },
  EQUAL_TO("==") {
    @Override
    public boolean compare(double expected, double actual) {
      return expected == actual;
    }
  },
  LARGER_THAN(">") {
    @Override
    public boolean compare(double expected, double actual) {
      return expected > actual;
    }
  },
  LARGER_THAN_OR_EQUAL_TO(">=") {
    @Override
    public boolean compare(double expected, double actual) {
      return expected >= actual;
    }
  };

  private final String literal;

  Comparison(final String literal) {
    this.literal = literal;
  }

  public abstract boolean compare(final double expected, final double actual);

  public static Comparison from(final String actual) {
    for (final Comparison comparison : values()) {
      if (comparison.literal.equals(actual.trim())) {
        return comparison;
      }
    }

    throw new IllegalArgumentException(
        String.format("Comparison operator '%s' is not recognized; valid values are: %s",
            actual, EnumUtils.toString(values())));
  }

}
