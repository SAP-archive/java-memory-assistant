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
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class LoggerImpl implements Logger {

  private static final ConcurrentMap<Class<?>, LoggerImpl> LOGGERS =
      new ConcurrentHashMap<>();

  // VisibleForTest
  static Severity LOG_LEVEL = Configuration.DEFAULT_LOG_LEVEL;

  private static PrintWriter OUT =
      new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));

  private static PrintWriter ERR =
      new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8));

  private final Class<?> clazz;

  private LoggerImpl(final Class<?> clazz) {
    this.clazz = clazz;
  }

  static void initialize(final Severity severity) {
    initialize(severity, System.out, System.err);
  }

  // VisibleForTesting
  static void initialize(final Severity severity, final PrintStream out, final PrintStream err) {
    LOG_LEVEL = severity;

    final Charset cs;
    final String fileEncoding = System.getProperty("file.encoding");
    if (fileEncoding == null || fileEncoding.trim().isEmpty()) {
      cs = StandardCharsets.UTF_8;
    } else {
      cs = Charset.forName(fileEncoding);
    }

    LoggerImpl.OUT = new PrintWriter(new OutputStreamWriter(out, cs));
    LoggerImpl.ERR = new PrintWriter(new OutputStreamWriter(err, cs));
  }

  public static LoggerImpl get(final Class<?> clazz) {
    final LoggerImpl newLogger = new LoggerImpl(clazz);
    final LoggerImpl actualLogger = LOGGERS.putIfAbsent(clazz, newLogger);

    return actualLogger == null ? newLogger : actualLogger;
  }

  // VisibleForTesting
  boolean isSeverityEnabled(final Severity severity) {
    return LOG_LEVEL.ordinal() >= severity.ordinal();
  }

  private void log(final Class<?> clazz, final Severity severity, final CharSequence message,
                   final Throwable throwable, final Object... args) {
    if (!isSeverityEnabled(severity)) {
      return;
    }

    final StringWriter sb = new StringWriter();
    try (final PrintWriter pw = new PrintWriter(sb)) {
      if (args.length == 0) {
        pw.append(message);
      } else {
        new Formatter(pw).format(message.toString(), args);
      }

      pw.println();

      if (throwable != null) {
        throwable.printStackTrace(pw);
      }
    }

    log(clazz, severity, sb.getBuffer());
  }

  private void log(final Class<?> clazz, final Severity severity, final CharSequence message) {
    final PrintWriter out = (severity == Severity.ERROR) ? LoggerImpl.ERR : LoggerImpl.OUT;

    out.print(clazz.getName() + "#" + severity + ": " + message);
    out.flush();
  }

  @Override
  public void error(final CharSequence message) {
    log(clazz, Severity.ERROR, message, null);
  }

  @Override
  public void error(final CharSequence message, final Throwable throwable) {
    log(clazz, Severity.ERROR, message, throwable);
  }

  @Override
  public void error(final CharSequence message, final Object arg0, final Throwable throwable) {
    log(clazz, Severity.ERROR, message, throwable, arg0);
  }

  @Override
  public void error(final CharSequence message, final Object arg0, final Object arg1,
                    final Throwable throwable) {
    log(clazz, Severity.ERROR, message, throwable, arg0, arg1);
  }

  @Override
  public void error(final CharSequence message, final Object arg0, final Object arg1,
                    final Object arg2, final Throwable throwable) {
    log(clazz, Severity.ERROR, message, throwable, arg0, arg1, arg2);
  }

  @Override
  public void error(final CharSequence message, final Object arg0, final Object arg1,
                    final Object arg2, final Object arg3, final Throwable throwable) {
    log(clazz, Severity.ERROR, message, throwable, arg0, arg1, arg3);
  }

  @Override
  public void error(final CharSequence message, final Object arg0, final Object arg1,
                    final Object arg2, final Object arg3, final Object arg4,
                    final Throwable throwable) {
    log(clazz, Severity.ERROR, message, throwable, arg0, arg1, arg2, arg3, arg4);
  }

  @Override
  public void error(final CharSequence message, final Object arg0, final Object arg1,
                    final Object arg2, final Object arg3, final Object arg4, final Object arg5,
                    final Throwable throwable) {
    log(clazz, Severity.ERROR, message, throwable, arg0, arg1, arg2, arg3, arg4, arg5);
  }

  @Override
  public void error(final CharSequence message, final Object arg0, final Object arg1,
                    final Object arg2, final Object arg3, final Object arg4, final Object arg5,
                    final Object arg6, final Throwable throwable) {
    log(clazz, Severity.ERROR, message, throwable, arg0, arg1, arg2, arg3, arg4, arg5, arg6);
  }

  @Override
  public void warning(final CharSequence message, final Object ... args) {
    log(clazz, Severity.WARNING, message, null, args);
  }

  @Override
  public void info(final CharSequence message, final Object ... args) {
    log(clazz, Severity.INFO, message, null, args);
  }

  @Override
  public void debug(final CharSequence message, final Object ... args) {
    log(clazz, Severity.DEBUG, message, null, args);
  }

}
