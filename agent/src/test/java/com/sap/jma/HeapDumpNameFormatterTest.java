/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import static com.sap.jma.HeapDumpNameFormatter.splitInParts;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import com.sap.jma.testapi.TemporaryDefaultTimeZone;
import java.util.Date;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.ExpectedException;

public class HeapDumpNameFormatterTest {

  @Rule
  public final TemporaryDefaultTimeZone tempDefaultTimeZone = TemporaryDefaultTimeZone.toBe("UTC");

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  private final Date date = new Date(0);

  private final UUID uuid = UUID.fromString("19866c22-ce15-41de-807b-4805d0387d76");

  private final HeapDumpNameFormatter.Provider<UUID> uuidProvider =
      new HeapDumpNameFormatter.Provider<UUID>() {
    @Override
    public UUID get() {
      return uuid;
    }
  };

  @Test
  public void testSplitInParts() {
    assertThat(splitInParts(" a "), contains(" a "));
    assertThat(splitInParts("a%"), contains("a", "%"));
    assertThat(splitInParts("a%b"), contains("a", "%b"));
    assertThat(splitInParts("%a"), contains("%a"));
    assertThat(splitInParts("%a%%"), contains("%a%%"));
    assertThat(splitInParts("%a%"), contains("%a%"));
    assertThat(splitInParts("a%%b"), contains("a%%b"));
    assertThat(splitInParts("a%b%"), contains("a", "%b%"));
    assertThat(splitInParts("a%%b%c%d"), contains("a%%b", "%c%", "d"));
    assertThat(splitInParts("a%b%%c%d"), contains("a", "%b%%c%", "d"));
    assertThat(splitInParts("a%b%%c%d%"), contains("a", "%b%%c%", "d", "%"));
    assertThat(splitInParts("a%b%%c%d%%"), contains("a", "%b%%c%", "d%%"));
    assertThat(splitInParts("%%a"), contains("%%a"));
    assertThat(splitInParts("a%%"), contains("a%%"));
    assertThat(splitInParts("%%%"), contains("%%", "%"));
    assertThat(splitInParts("%%%%"), contains("%%%%"));
    assertThat(splitInParts("%%a%%"), contains("%%a%%"));
    assertThat(splitInParts("%%%a%%%"), contains("%%", "%a%%%"));
  }

  @Test
  public void testNamePatternEmpty() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the pattern cannot be empty"));

    HeapDumpNameFormatter.validate("");
  }

  @Test
  public void testNamePatternBlank() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the pattern cannot be blank"));

    HeapDumpNameFormatter.validate("  \t  ");
  }

  @Test
  public void testNamePatternEscape() {
    final HeapDumpNameFormatter subject = new HeapDumpNameFormatter("my%%heapdump", "my_host");

    assertThat(subject.format(date), is("my%heapdump"));
  }

  @Test
  public void testNamePatternEscapeMidToken() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token '%to%%ken%' (position 2 to 10) is unknown"));

    HeapDumpNameFormatter.validate("my%to%%ken%yolo");
  }

  @Test
  public void testNamePatternMissingTokenName() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        is("the name is missing from the token '%:ciao%' (position 2 to 8)"));

    HeapDumpNameFormatter.validate("my%:ciao%yolo");
  }

  @Test
  public void testNamePatternEscapeBegin() {
    final HeapDumpNameFormatter subject = new HeapDumpNameFormatter("%%token", "my_host");

    assertThat(subject.format(date), is("%token"));
  }

  @Test
  public void testNamePatternEscapeEnd() {
    final HeapDumpNameFormatter subject = new HeapDumpNameFormatter("token%%", "my_host");

    assertThat(subject.format(date), is("token%"));
  }

  @Test
  public void testNamePatternDoubleBackslash() {
    final HeapDumpNameFormatter subject = new HeapDumpNameFormatter("my\\\\heapdump", "my_host");

    assertThat(subject.format(date), is("my\\\\heapdump"));
  }

  @Test
  public void testNamePatternCountingPercentTokens() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        is("the token starter character '%' at position 13 does not have a matching token closer "
            + "'%' character"));

    HeapDumpNameFormatter.validate("a%host_name%c%");
  }

  @Test
  public void testNamePatternCountingPercentTokens2() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token starter character '%' at position 13 does not "
        + "have a matching token closer '%' character"));

    HeapDumpNameFormatter.validate("a%host_name%c%ddd");
  }

  @Test
  public void testNamePatternCountingPercentTokens3() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token starter character '%' at position 2 does not "
        + "have a matching token closer '%' character"));

    HeapDumpNameFormatter.validate("%%%");
  }

  @Test
  public void testNamePatternBackslash() {
    final HeapDumpNameFormatter subject = new HeapDumpNameFormatter("a\\b", "my_host");

    assertThat(subject.format(date), is("a\\b"));
  }

  @Test
  public void testNamePatternUnknownToken() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token '%ciao%' (position 1 to 6) is unknown"));

    HeapDumpNameFormatter.validate("a%ciao%b");
  }

  @Test
  public void testNamePatternValidTokens() {
    final HeapDumpNameFormatter subject =
        new HeapDumpNameFormatter("hda_%host_name%_%ts:yyyyMMdd%_%uuid%.hprof",
            "my_host",uuidProvider);

    assertThat(subject.format(date),
        is("hda_my_host_19700101_19866c22-ce15-41de-807b-4805d0387d76.hprof"));
  }

  @Test
  public void testNamePatternHostNameEmptyConfiguration() {
    HeapDumpNameFormatter.validate("hda_%host_name:%.hprof");
  }

  @Test
  public void testNamePatternHostNameNonEmptyConfiguration() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token '%host_name:a%' (position 4 to 16) has invalid "
        + "configuration: it does not support configuration values provided after the ':' "
        + "character, but 'a' is provided"));

    HeapDumpNameFormatter.validate("hda_%host_name:a%.hprof");
  }

  @Test
  public void testNamePatternUuidEmptyConfiguration() {
    HeapDumpNameFormatter.validate("hda_%uuid:%.hprof");
  }

  @Test
  public void testNamePatternUuidNonEmptyConfiguration() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token '%uuid:a%' (position 4 to 11) has invalid "
        + "configuration: it does not support configuration values provided after the ':' "
        + "character, but 'a' is provided"));

    HeapDumpNameFormatter.validate("hda_%uuid:a%.hprof");
  }

  @Test
  public void testNamePatternNoTimestampFormatting() {
    final HeapDumpNameFormatter subject =
        new HeapDumpNameFormatter("hda_%host_name%_%ts:%_%uuid%.hprof", "my_host", uuidProvider);

    assertThat(subject.format(date),
        is("hda_my_host_0_19866c22-ce15-41de-807b-4805d0387d76.hprof"));
  }

  @Test
  public void testNamePatternTimestampFormattingWithEscape() {
    final HeapDumpNameFormatter subject =
        new HeapDumpNameFormatter("hda_%host_name%_%ts:yyyy'%%'MM'%%'dd%_%uuid%.hprof",
            "my_host", uuidProvider);

    assertThat(subject.format(date),
        is("hda_my_host_1970%01%01_19866c22-ce15-41de-807b-4805d0387d76.hprof"));
  }

  @Test
  public void testNamePatternTimestampFormattingMillis() {
    final HeapDumpNameFormatter subject =
        new HeapDumpNameFormatter("hda_%host_name%_%ts:yyyyMMddmmssSS%_%uuid%.hprof",
            "my_host", uuidProvider);

    assertThat(subject.format(date),
        is("hda_my_host_19700101000000_19866c22-ce15-41de-807b-4805d0387d76.hprof"));
  }

  @Test
  public void testNamePatternTimestampFormattingTimeZone() {
    final HeapDumpNameFormatter subject =
        new HeapDumpNameFormatter("hda_%host_name%_%ts:yyyyMMddmmssSSZZ%_%uuid%.hprof",
            "my_host", uuidProvider);

    assertThat(subject.format(date),
        is("hda_my_host_19700101000000+0000_19866c22-ce15-41de-807b-4805d0387d76.hprof"));
  }

  @Test
  public void testNamePatternNoTimestampFormattingNoColon() {
    final HeapDumpNameFormatter subject =
        new HeapDumpNameFormatter("hda_%host_name%_%ts%_%uuid%.hprof", "my_host", uuidProvider);

    assertThat(subject.format(date),
        is("hda_my_host_0_19866c22-ce15-41de-807b-4805d0387d76.hprof"));
  }

  @Test
  public void testNamePatternInvalidDatePattern() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token '%ts:abc%' (position 1 to 8) has invalid "
        + "configuration: the date formatting pattern 'abc' is invalid; for supported patterns, "
        + "consult the javadoc of java.text.SimpleDateFormat"));

    HeapDumpNameFormatter.validate("a%ts:abc%b");
  }

  @Test
  public void testNamePatternEnvironmentNonEmptyConfiguration() {
    HeapDumpNameFormatter.validate("hda_%env:abc%.hprof");
  }

  @Test
  public void testNamePatternEnvironmentEmptyConfiguration() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token '%env:%' (position 4 to 9) has invalid "
        + "configuration: it requires configuration, but none is provided"));

    HeapDumpNameFormatter.validate("hda_%env:%.hprof");
  }

  @Test
  public void testNamePatternEnvironmentWithLiterals() {
    environmentVariables.set("CF_INSTANCE_INDEX", "42");
    environmentVariables.set("CF_INSTANCE_GUID", "0987654321");

    HeapDumpNameFormatter.validate("%env:CF_INSTANCE_INDEX%-%ts:yyyy-MM-dd'T'mm':'ss':'SSSZ%-"
        + "%env:CF_INSTANCE_GUID[,8]%.hprof");

    final HeapDumpNameFormatter subject =
        new HeapDumpNameFormatter("%env:CF_INSTANCE_INDEX%-%ts:yyyy-MM-dd'T'mm':'ss':'SSSZ%-"
            + "%env:CF_INSTANCE_GUID[,8]%.hprof", "my_host", uuidProvider);

    assertThat(subject.format(date), is("42-1970-01-01T00:00:000+0000-09876543.hprof"));
  }

  @Test
  public void testNamePatternEnvironmentPropertyNotSet() {
    assertThat(System.getenv("my_prop"), nullValue());

    final HeapDumpNameFormatter subject =
        new HeapDumpNameFormatter("hda_%env:my_prop%.hprof", "my_host", uuidProvider);

    assertThat(subject.format(date), is("hda_.hprof"));
  }

  @Test
  public void testNamePatternEnvironmentPropertyTruncatedClosedSpan() {
    environmentVariables.set("my_prop", "abcdefghi");

    final HeapDumpNameFormatter subject =
        new HeapDumpNameFormatter("hda_%env:my_prop[2,4]%.hprof", "my_host", uuidProvider);

    assertThat(subject.format(date), is("hda_cd.hprof"));
  }

  @Test
  public void testNamePatternEnvironmentPropertyTruncatedFromBeginning() {
    environmentVariables.set("my_prop", "abcdefghi");

    final HeapDumpNameFormatter subject =
        new HeapDumpNameFormatter("hda_%env:my_prop[,4]%.hprof", "my_host", uuidProvider);

    assertThat(subject.format(date), is("hda_abcd.hprof"));
  }

  @Test
  public void testNamePatternEnvironmentPropertyTruncatedOpenEnd() {
    environmentVariables.set("my_prop", "abcdefghi");

    final HeapDumpNameFormatter subject =
        new HeapDumpNameFormatter("hda_%env:my_prop[4,]%.hprof", "my_host", uuidProvider);

    assertThat(subject.format(date), is("hda_efghi.hprof"));
  }

  @Test
  public void testNamePatternEnvironmentInvalidTruncation_1() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token '%env:abc[%' (position 4 to 13) has invalid "
        + "configuration: it must match the '(?:(\\w+)(?:\\[(\\d+)?,(\\d+)?])?)?' pattern"));

    HeapDumpNameFormatter.validate("hda_%env:abc[%.hprof");
  }

  @Test
  public void testNamePatternEnvironmentInvalidTruncation_2() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token '%env:abc]%' (position 4 to 13) has invalid "
        + "configuration: it must match the '(?:(\\w+)(?:\\[(\\d+)?,(\\d+)?])?)?' pattern"));

    HeapDumpNameFormatter.validate("hda_%env:abc]%.hprof");
  }

  @Test
  public void testNamePatternEnvironmentInvalidTruncation_3() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token '%env:abc[,%' (position 4 to 14) has invalid "
        + "configuration: it must match the '(?:(\\w+)(?:\\[(\\d+)?,(\\d+)?])?)?' pattern"));

    HeapDumpNameFormatter.validate("hda_%env:abc[,%.hprof");
  }

  @Test
  public void testNamePatternEnvironmentInvalidTruncation_4() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("the token '%env:abc[a,b]%' (position 4 to 17) has invalid "
        + "configuration: it must match the '(?:(\\w+)(?:\\[(\\d+)?,(\\d+)?])?)?' pattern"));

    HeapDumpNameFormatter.validate("hda_%env:abc[a,b]%.hprof");
  }

}