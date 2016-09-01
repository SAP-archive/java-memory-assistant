/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.time;

public interface Clock {

  Clock SYSTEM = new Clock() {
    @Override
    public long getMillis() {
      return java.lang.System.currentTimeMillis();
    }
  };

  long getMillis();

}
