/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.logging;

public interface Logger {

  void error(CharSequence message);

  void error(CharSequence message, Throwable throwable);

  void error(CharSequence message, Object arg0, Throwable throwable);

  void error(CharSequence message, Object arg0, Object arg1, Throwable throwable);

  void error(CharSequence message, Object arg0, Object arg1, Object arg2, Throwable throwable);

  void error(CharSequence message, Object arg0, Object arg1, Object arg2, Object arg3,
             Throwable throwable);

  void error(CharSequence message, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4,
             Throwable throwable);

  void error(CharSequence message, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4,
             Object arg5, Throwable throwable);

  void error(CharSequence message, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4,
             Object arg5, Object arg6, Throwable throwable);

  void warning(CharSequence message, Object ... args);

  void info(CharSequence message, Object ... args);

  void debug(CharSequence message, Object ... args);

  enum Severity {
    OFF, ERROR, INFO, WARNING, DEBUG
  }

  class Factory {

    private Factory() {
    }

    public static void initialize(Severity severity) {
      LoggerImpl.initialize(severity);
    }

    public static Logger get(Class<?> clazz) {
      return LoggerImpl.get(clazz);
    }

  }

}