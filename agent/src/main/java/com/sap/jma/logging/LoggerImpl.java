/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.logging;

import com.sap.jma.configuration.Configuration;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class LoggerImpl implements Logger {

  private static final ConcurrentMap<Class<?>, LoggerImpl> LOGGERS =
      new ConcurrentHashMap<Class<?>, LoggerImpl>();

  // VisibleForTest
  static Severity logLevel = Configuration.DEFAULT_LOG_LEVEL;

  private static PrintWriter out =
      new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));

  private static PrintWriter err =
      new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8));
  private final Class<?> clazz;

  private LoggerImpl(final Class<?> clazz) {
    this.clazz = clazz;
  }

  public static void initialize(final Severity severity) {
    initialize(severity, System.out, System.err);
  }

  // VisibleForTesting
  static void initialize(final Severity severity, final PrintStream out, final PrintStream err) {
    logLevel = severity;

    final Charset cs;
    final String fileEncoding = System.getProperty("file.encoding");
    if (fileEncoding == null || fileEncoding.trim().isEmpty()) {
      cs = StandardCharsets.UTF_8;
    } else {
      cs = Charset.forName(fileEncoding);
    }

    LoggerImpl.out = new PrintWriter(new OutputStreamWriter(out, cs));
    LoggerImpl.err = new PrintWriter(new OutputStreamWriter(err, cs));
  }

  public static LoggerImpl get(final Class<?> clazz) {
    final LoggerImpl newLogger = new LoggerImpl(clazz);
    final LoggerImpl actualLogger = LOGGERS.putIfAbsent(clazz, newLogger);

    return actualLogger == null ? newLogger : actualLogger;
  }

  // VisibleForTesting
  boolean isSeverityEnabled(final Severity severity) {
    return logLevel.ordinal() >= severity.ordinal();
  }

  private void log(final Class<?> clazz, final Severity severity, final String message,
                   final Throwable throwable) {
    final PrintWriter out;

    switch (severity) {
      case ERROR:
        out = LoggerImpl.err;
        break;
      default:
        out = LoggerImpl.out;
    }

    out.print(clazz.getName() + "#" + severity + ": " + message + '\n');
    if (throwable != null) {
      throwable.printStackTrace(out);
    }
    out.flush();
  }

  private void log(final Severity severity, final String message, final Throwable throwable) {
    if (isSeverityEnabled(severity)) {
      log(clazz, severity, message, throwable);
    }
  }

  private void log(final Severity level, final String message) {
    log(level, message, null);
  }

  @Override
  public void error(final String message) {
    log(Severity.ERROR, message);
  }

  @Override
  public void error(final String message, final Throwable throwable) {
    log(Severity.ERROR, message, throwable);
  }

  @Override
  public void warning(final String message) {
    log(Severity.WARNING, message);
  }

  @Override
  public void info(final String message) {
    log(Severity.INFO, message);
  }

  @Override
  public void debug(final String message) {
    log(Severity.DEBUG, message);
  }

}
