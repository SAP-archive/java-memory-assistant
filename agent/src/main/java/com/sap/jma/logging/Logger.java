/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.logging;

public interface Logger {

  void error(String message);

  void error(String message, Throwable throwable);

  void warning(String message);

  void info(String message);

  void debug(String message);

  enum Severity {
    OFF, ERROR, INFO, WARNING, DEBUG
  }

  class Factory {

    private Factory() {
    }

    public static void initialize(final Severity severity) {
      LoggerImpl.initialize(severity);
    }

    public static Logger get(Class<?> clazz) {
      return LoggerImpl.get(clazz);
    }

  }

}