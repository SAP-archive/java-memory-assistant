/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.logging;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.sap.jma.configuration.Configuration;
import com.sap.jma.testapi.TemporarySystemProperties;
import java.io.ByteArrayOutputStream;
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

public class LoggerTest {

  @Rule
  public final TemporarySystemProperties temporarySystemProperties =
      TemporarySystemProperties.overlay();

  private final LoggerImpl subject = spy(LoggerImpl.get(LoggerTest.class));

  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;

  private static Matcher<ByteArrayOutputStream> containsLines(final String... expectedLines) {
    return containsLines(StandardCharsets.UTF_8, expectedLines);
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
        StandardCharsets.UTF_8.name()), new PrintStream(err, true, StandardCharsets.UTF_8.name()));
  }

  @Before
  @After
  public void resetLogger() {
    LoggerImpl.logLevel = Configuration.DEFAULT_LOG_LEVEL;
  }

  @Test
  public void testLogThrowable() throws Exception {
    final Throwable toBeThrown = new Throwable("fail");
    final String toBeThrownToString;
    try (final ByteArrayOutputStream out = new ByteArrayOutputStream();
         final PrintWriter pOut =
             new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
      toBeThrown.printStackTrace(pOut);
      pOut.flush();
      toBeThrownToString = new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    subject.error("test", toBeThrown);

    assertThat(new String(err.toByteArray(), StandardCharsets.UTF_8),
        is("com.sap.jma.logging.LoggerTest#ERROR: test\n" + toBeThrownToString));
  }

  @Test
  public void testLoggingOff() {
    LoggerImpl.logLevel = LoggerImpl.Severity.OFF;

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
    LoggerImpl.logLevel = LoggerImpl.Severity.ERROR;

    subject.error("1");
    subject.info("2");
    subject.warning("3");
    subject.debug("4");

    assertThat(err, containsLines(LoggerTest.class.getName() + "#ERROR: 1"));

    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.ERROR), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.INFO), is(false));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.WARNING), is(false));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.DEBUG), is(false));
  }

  @Test
  public void testIsSeverityEnabledInfo() {
    LoggerImpl.logLevel = LoggerImpl.Severity.INFO;

    subject.error("1");
    subject.info("2");
    subject.warning("3");
    subject.debug("4");

    assertThat(err, containsLines(LoggerTest.class.getName() + "#ERROR: 1"));
    assertThat(out, containsLines(LoggerTest.class.getName() + "#INFO: 2"));

    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.ERROR), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.INFO), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.WARNING), is(false));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.DEBUG), is(false));
  }

  @Test
  public void testIsSeverityEnabledWarning() {
    LoggerImpl.logLevel = LoggerImpl.Severity.WARNING;

    subject.error("1");
    subject.info("2");
    subject.warning("3");
    subject.debug("4");

    assertThat(err, containsLines(LoggerTest.class.getName() + "#ERROR: 1"));
    assertThat(out, containsLines(LoggerTest.class.getName() + "#INFO: 2",
        LoggerTest.class.getName() + "#WARNING: 3"));

    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.ERROR), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.INFO), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.WARNING), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.DEBUG), is(false));
  }

  @Test
  public void testIsSeverityEnabledDebug() {
    LoggerImpl.logLevel = LoggerImpl.Severity.DEBUG;

    subject.error("1");
    subject.info("2");
    subject.warning("3");
    subject.debug("4");

    assertThat(err, containsLines(LoggerTest.class.getName() + "#ERROR: 1"));
    assertThat(out, containsLines(LoggerTest.class.getName() + "#INFO: 2",
        LoggerTest.class.getName() + "#WARNING: 3", LoggerTest.class.getName() + "#DEBUG: 4"));

    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.ERROR), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.INFO), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.WARNING), is(true));
    assertThat(subject.isSeverityEnabled(LoggerImpl.Severity.DEBUG), is(true));
  }

  @Test
  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  public void testUtf8() {
    subject.error("\u0048\u0065\u006C\u006C\u006F World");

    assertThat(err, containsLines(LoggerTest.class.getName() + "#ERROR: Hello World"));
  }

  @Test
  public void testDefaultFileEncoding() throws Exception {
    temporarySystemProperties.set("file.encoding")
        .to(StandardCharsets.UTF_16BE.name().toLowerCase());

    LoggerImpl.initialize(LoggerImpl.Severity.ERROR, new PrintStream(out, true,
        StandardCharsets.UTF_8.name()), new PrintStream(err, true, StandardCharsets.UTF_8.name()));

    subject.error("Hello World");

    assertThat(err, containsLines(StandardCharsets.UTF_16BE, LoggerTest.class.getName()
        + "#ERROR: Hello World"));
  }

}
