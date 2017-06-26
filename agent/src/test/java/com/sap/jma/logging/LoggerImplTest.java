/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.logging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.sap.jma.configuration.Configuration;
import com.sap.jma.testapi.TemporarySystemProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LoggerImplTest {

  @Rule
  public final TemporarySystemProperties temporarySystemProperties =
      TemporarySystemProperties.overlay();

  private final LoggerImpl subject = spy(LoggerImpl.get(LoggerImplTest.class));

  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;

  private static Matcher<ByteArrayOutputStream> containsLines(final String... expectedLines) {
    return containsLines(UTF_8, expectedLines);
  }

  private static Matcher<ByteArrayOutputStream> containsLines(final Charset cs,
                                                              final String... expectedLines) {
    return new TypeSafeMatcher<ByteArrayOutputStream>() {
      @Override
      protected boolean matchesSafely(final ByteArrayOutputStream out) {
        final String content = new String(out.toByteArray(), cs);
        final Iterator<String> i = Arrays.asList(content.split("\\n")).iterator();

        for (final String expectedLine : expectedLines) {
          if (!i.hasNext()) {
            return false;
          }

          final String actualLine = i.next();
          if (!expectedLine.equals(actualLine)) {
            return false;
          }
        }

        return !i.hasNext();
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("Contains the following lines followed by a line end character: "
            + StringUtils.join(expectedLines, ", "));
      }
    };
  }

  @Before
  public void setupPrintWriter() throws Exception {
    temporarySystemProperties.set("file.encoding").to(null);

    out = spy(new ByteArrayOutputStream());
    err = spy(new ByteArrayOutputStream());

    LoggerImpl.initialize(LoggerImpl.Severity.ERROR, new PrintStream(out, true,
        UTF_8.name()), new PrintStream(err, true, UTF_8.name()));
  }

  @Before
  @After
  public void resetLogger() {
    LoggerImpl.LOG_LEVEL = Configuration.DEFAULT_LOG_LEVEL;
  }

  @Test
  public void testLogThrowableWithoutArguments() throws Exception {
    final Throwable toBeThrown = new Throwable("fail");
    final String toBeThrownToString = toString(toBeThrown);

    subject.error("test", toBeThrown);

    assertThat(new String(err.toByteArray(), UTF_8),
        is("com.sap.jma.logging.LoggerImplTest#ERROR: test\n" + toBeThrownToString));
  }

  @Test
  public void testLogThrowableWithArguments() throws Exception {
    final Throwable toBeThrown = new Throwable("fail");
    final String toBeThrownToString = toString(toBeThrown);

    subject.error("test %s%s%s%s%s", "a", "b", "c", "d", "e", toBeThrown);

    assertThat(new String(err.toByteArray(), UTF_8),
        is("com.sap.jma.logging.LoggerImplTest#ERROR: test abcde\n" + toBeThrownToString));
  }

  @Test
  public void testLogInfoWithArguments() throws Exception {
    LoggerImpl.LOG_LEVEL = LoggerImpl.Severity.INFO;

    subject.info("Hello %s", "World");

    assertThat(new String(out.toByteArray(), UTF_8),
        is("com.sap.jma.logging.LoggerImplTest#INFO: Hello World\n"));
  }

  @Test
  public void testLogWarnWithArguments() throws Exception {
    LoggerImpl.LOG_LEVEL = LoggerImpl.Severity.WARNING;

    subject.warning("Hello %s %s %s", "World", "Hello", "World");

    assertThat(new String(out.toByteArray(), UTF_8),
        is("com.sap.jma.logging.LoggerImplTest#WARNING: Hello World Hello World\n"));
  }

  @Test
  public void testLogDebugWithArguments() throws Exception {
    LoggerImpl.LOG_LEVEL = LoggerImpl.Severity.DEBUG;

    subject.debug("%s %s", "Hello", "World");

    assertThat(new String(out.toByteArray(), UTF_8),
        is("com.sap.jma.logging.LoggerImplTest#DEBUG: Hello World\n"));
  }

  @Test
  public void testLoggingOff() {
    LoggerImpl.LOG_LEVEL = LoggerImpl.Severity.OFF;

    subject.error("1");
    subject.info("2");
    subject.warning("3");
    subject.debug("4");

    verifyZeroInteractions(out);

    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.ERROR), is(false));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.INFO), is(false));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.WARNING), is(false));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.DEBUG), is(false));
  }

  @Test
  public void testIsSeverityEnabledError() {
    LoggerImpl.LOG_LEVEL = LoggerImpl.Severity.ERROR;

    subject.error("1");
    subject.info("2");
    subject.warning("3");
    subject.debug("4");

    assertThat(err, containsLines(LoggerImplTest.class.getName() + "#ERROR: 1"));

    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.ERROR), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.INFO), is(false));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.WARNING), is(false));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.DEBUG), is(false));
  }

  @Test
  public void testIsSeverityEnabledInfo() {
    LoggerImpl.LOG_LEVEL = LoggerImpl.Severity.INFO;

    subject.error("1");
    subject.info("2");
    subject.warning("3");
    subject.debug("4");

    assertThat(err, containsLines(LoggerImplTest.class.getName() + "#ERROR: 1"));
    assertThat(out, containsLines(LoggerImplTest.class.getName() + "#INFO: 2"));

    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.ERROR), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.INFO), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.WARNING), is(false));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.DEBUG), is(false));
  }

  @Test
  public void testIsSeverityEnabledWarning() {
    LoggerImpl.LOG_LEVEL = LoggerImpl.Severity.WARNING;

    subject.error("1");
    subject.info("2");
    subject.warning("3");
    subject.debug("4");

    assertThat(err, containsLines(LoggerImplTest.class.getName() + "#ERROR: 1"));
    assertThat(out, containsLines(LoggerImplTest.class.getName() + "#INFO: 2",
        LoggerImplTest.class.getName() + "#WARNING: 3"));

    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.ERROR), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.INFO), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.WARNING), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.DEBUG), is(false));
  }

  @Test
  public void testIsSeverityEnabledDebug() {
    LoggerImpl.LOG_LEVEL = LoggerImpl.Severity.DEBUG;

    subject.error("1");
    subject.info("2");
    subject.warning("3");
    subject.debug("4");

    assertThat(err, containsLines(LoggerImplTest.class.getName() + "#ERROR: 1"));
    assertThat(out, containsLines(LoggerImplTest.class.getName() + "#INFO: 2",
        LoggerImplTest.class.getName() + "#WARNING: 3",
        LoggerImplTest.class.getName() + "#DEBUG: 4"));

    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.ERROR), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.INFO), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.WARNING), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.DEBUG), is(true));
  }

  @Test
  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  public void testUtf8() {
    subject.error("\u0048\u0065\u006C\u006C\u006F World");

    assertThat(err, containsLines(LoggerImplTest.class.getName() + "#ERROR: Hello World"));
  }

  @Test
  public void testDefaultFileEncoding() throws Exception {
    temporarySystemProperties.set("file.encoding")
        .to(StandardCharsets.UTF_16BE.name().toLowerCase());

    LoggerImpl.initialize(LoggerImpl.Severity.ERROR, new PrintStream(out, true,
        UTF_8.name()), new PrintStream(err, true, UTF_8.name()));

    subject.error("Hello World");

    assertThat(err, containsLines(StandardCharsets.UTF_16BE, LoggerImplTest.class.getName()
        + "#ERROR: Hello World"));
  }

  private String toString(final Throwable throwable) throws IOException {
    try (final ByteArrayOutputStream out = new ByteArrayOutputStream();
         final PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, UTF_8))) {
      throwable.printStackTrace(pw);
      pw.flush();
      return new String(out.toByteArray(), UTF_8);
    }
  }

}
