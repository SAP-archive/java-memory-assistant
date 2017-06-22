/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.utils;

public class EnumUtils {

  private EnumUtils() {}

  public static String toString(final Enum<? extends Enum>[] array) {
    if (array == null || array.length < 1) {
      return "";
    }

    final StringBuilder sb = new StringBuilder();
    for (int i = 0, l = array.length; i < l; ++i) {
      sb.append(array[i].toString());
      if (i < l - 1) {
        sb.append(", ");
      }
    }

    return sb.toString();
  }

}