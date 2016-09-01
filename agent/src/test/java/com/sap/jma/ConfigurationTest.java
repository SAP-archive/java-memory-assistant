/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import static com.sap.jma.testapi.Matchers.StringMatchers.hasLines;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sap.jma.Configuration.IncreaseOverTimeFrameThresholdConfiguration;
import com.sap.jma.Configuration.Property;
import com.sap.jma.logging.Logger;
import com.sap.jma.testapi.TemporarySystemProperties;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ConfigurationTest {

  @Rule
  public final TemporarySystemProperties temporarySystemProperties =
      TemporarySystemProperties.overlay();

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  private final Logger logger = mock(Logger.class);

  private static Matcher<Configuration.ThresholdConfiguration>
        hasIncreaseOverTimeFrameValue(final double increase, final long timeFrameInMillis) {
    return new BaseMatcher<Configuration.ThresholdConfiguration>() {
      @Override
      public boolean matches(Object obj) {
        try {
          final IncreaseOverTimeFrameThresholdConfiguration config =
              (IncreaseOverTimeFrameThresholdConfiguration) obj;

          return increase == config.getDelta()
              && timeFrameInMillis == config.getTimeUnit().toMilliSeconds(config.getTimeFrame());
        } catch (ClassCastException ex) {
          return false;
        }
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(
            String.format("has delta '%.2f' over time-frame '%d' in millis",
                increase, timeFrameInMillis));
      }
    };
  }

  private static Matcher<Configuration.ThresholdConfiguration>
        hasPercentageValue(final double expected) {
    return new BaseMatcher<Configuration.ThresholdConfiguration>() {
      @Override
      public boolean matches(Object obj) {
        return obj instanceof Configuration.PercentageThresholdConfiguration
            && ((Configuration.PercentageThresholdConfiguration) obj).getValue() == expected;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(String.format("has percentage usage value '%.2f'", expected));
      }
    };
  }

  @After
  public void tearDown() {
    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testLegacyPrefix() {
    temporarySystemProperties.set("hdagent.check_interval").to("4h");

    final Configuration subject =
        Configuration.Builder.initializeFromSystemProperties(logger).build();

    assertThat(subject.getCheckIntervalInMillis(), is(TimeUnit.HOURS.toMillis(4)));
  }

  @Test
  public void testDefaults() {
    final Configuration subject =
        Configuration.Builder.initializeFromSystemProperties(logger).build();

    assertThat(subject.isEnabled(), is(false));

    assertThat(subject.getMaxFrequency(), nullValue());

    assertThat(subject.getHeapDumpName(), is(Configuration.DEFAULT_NAME_PATTERN));

    assertThat(subject.getHeapDumpFolder(), is(new File(System.getProperty("user.dir"))));

    assertThat(subject.getLogLevel(), Matchers.is(Logger.Severity.ERROR));

    assertThat(subject.getCheckIntervalInMillis(), is(-1L));

    assertThat(subject.getCodeCacheMemoryUsageThreshold(), nullValue());
    assertThat(subject.getCompressedClassSpaceMemoryUsageThreshold(), nullValue());
    assertThat(subject.getEdenSpaceMemoryUsageThreshold(), nullValue());
    assertThat(subject.getHeapMemoryUsageThreshold(), nullValue());
    assertThat(subject.getMetaspaceMemoryUsageThreshold(), nullValue());
    assertThat(subject.getOldGenSpaceMemoryUsageThreshold(), nullValue());
    assertThat(subject.getSurvivorSpaceMemoryUsageThreshold(), nullValue());

    assertThat(subject.getCommandInterpreter(), not(nullValue()));

    assertThat(subject.getExecuteAfter(), nullValue());
    assertThat(subject.getExecuteBefore(), nullValue());
  }

  @Test
  public void testSystemProperties() {
    temporarySystemProperties.set("user.dir").to("/test");

    temporarySystemProperties.set(Property.ENABLED.getQualifiedName())
        .to("false");

    temporarySystemProperties.set(Property.MAX_HEAP_DUMP_FREQUENCY.getQualifiedName())
        .to("4/2h");

    temporarySystemProperties.set(Property.LOG_LEVEL.getQualifiedName())
        .to("DEBUG");

    temporarySystemProperties
        .set(Property.CHECK_INTERVAL.getQualifiedName()).to("42s");

    temporarySystemProperties
        .set(Property.CODE_CACHE_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("1%");

    temporarySystemProperties.set(
        Property.COMPRESSED_CLASS_SPACE_MEMORY_USAGE_THRESHOLD.getQualifiedName()).to("2%");

    temporarySystemProperties
        .set(Property.EDEN_SPACE_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("+3%/1m");

    temporarySystemProperties
        .set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("4%");

    temporarySystemProperties
        .set(Property.METASPACE_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("+5%/10ms");

    temporarySystemProperties
        .set(Property.OLD_GEN_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("6.4%");

    temporarySystemProperties.set(
        Property.SURVIVOR_SPACE_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("7.42%");

    temporarySystemProperties.set(
        Property.COMMAND_INTERPRETER.getQualifiedName())
        .to("sh");

    temporarySystemProperties.set(
        Property.EXECUTE_BEFORE_HEAP_DUMP.getQualifiedName())
        .to("./ciao-before");

    temporarySystemProperties.set(
        Property.EXECUTE_AFTER_HEAP_DUMP.getQualifiedName())
        .to("./ciao-after");

    final Configuration configuration =
        Configuration.Builder.initializeFromSystemProperties(logger).build();

    assertThat(configuration.isEnabled(), is(false));

    final Configuration.HeapDumpExecutionFrequency maxFrequency = configuration.getMaxFrequency();
    assertThat(maxFrequency, notNullValue());
    assertThat(maxFrequency.getExecutionAmount(), is(4));
    assertThat(maxFrequency.getTimeFrameInMillis(), is(2 * 60 * 60 * 1000L));

    assertThat(configuration.getHeapDumpFolder().getPath(), is(File.separator + "test"));
    assertThat(configuration.getLogLevel(), is(Logger.Severity.DEBUG));
    assertThat(configuration.getCheckIntervalInMillis(), is(42000L));
    assertThat(configuration.getCodeCacheMemoryUsageThreshold(), hasPercentageValue(1d));
    assertThat(configuration.getCompressedClassSpaceMemoryUsageThreshold(), hasPercentageValue(2d));
    assertThat(configuration.getEdenSpaceMemoryUsageThreshold(),
        hasIncreaseOverTimeFrameValue(3d, 60 * 1000L));
    assertThat(configuration.getHeapMemoryUsageThreshold(), hasPercentageValue(4d));
    assertThat(configuration.getMetaspaceMemoryUsageThreshold(),
        hasIncreaseOverTimeFrameValue(5d, 10L));
    assertThat(configuration.getOldGenSpaceMemoryUsageThreshold(), hasPercentageValue(6.4d));
    assertThat(configuration.getSurvivorSpaceMemoryUsageThreshold(), hasPercentageValue(7.42d));

    assertThat(configuration.getCommandInterpreter(), is("sh"));
    assertThat(configuration.getExecuteAfter(), is("./ciao-after"));
    assertThat(configuration.getExecuteBefore(), is("./ciao-before"));
  }

  @Test
  public void testMultipleErrors() {
    temporarySystemProperties.set(Property.LOG_LEVEL.getQualifiedName())
        .to("NOPE 1");
    temporarySystemProperties.set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("NOPE 2");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(hasLines("There are invalid configuration values:", //
        "* The value 'NOPE 1' is invalid for the 'jma.log_level' property: "
        + "allowed values are: OFF, ERROR, INFO, WARNING, DEBUG", //
        "* The value 'NOPE 2' is invalid for the 'jma.thresholds.heap' property: "
        + "it must follow the Java pattern '\\+(\\d*\\.?\\d*\\d)%/(\\d*\\.?\\d*\\d)(ms|s|m|h)'"
    ));

    Configuration.Builder.initializeFromSystemProperties(logger).build();
  }

  @Test
  public void testCheckIntervalValidation() {
    temporarySystemProperties.set(Property.ENABLED.getQualifiedName())
        .to("true");
    temporarySystemProperties.set(Property.CHECK_INTERVAL.getQualifiedName())
        .to("10s");
    temporarySystemProperties.set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("+5%/1s");
    temporarySystemProperties.set(Property.EDEN_SPACE_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("+5%/20s");

    Configuration.Builder.initializeFromSystemProperties(logger).build();

    verify(logger).debug("Property '" + Property.ENABLED.getQualifiedName()
        + "' set via environment to: 'true'");
    verify(logger).debug("Property '" + Property.CHECK_INTERVAL.getQualifiedName()
        + "' set via environment to: '10s'");
    verify(logger).debug("Property '" + Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName()
        + "' set via environment to: '+5%/1s'");
    verify(logger).debug("Property '"
        + Property.EDEN_SPACE_MEMORY_USAGE_THRESHOLD.getQualifiedName()
        + "' set via environment to: '+5%/20s'");

    verify(logger).warning("The provided configurations have the following issues:\n"
        + "* the time-frame for the threshold for memory pool 'Heap' of 1.0s is too short "
        + "compared to the overall check-interval of 10000ms: to ensure a good precision, "
        + "the ratio between check-interval and time-frame can be at most 1:2");
  }

  @Test
  public void testValidLogLevels() {
    for (final Logger.Severity severity : Logger.Severity.values()) {
      temporarySystemProperties.set(Property.LOG_LEVEL.getQualifiedName())
          .to(severity.name());

      final Configuration configuration =
          Configuration.Builder.initializeFromSystemProperties(logger).build();

      assertThat(configuration.getLogLevel(), is(severity));
    }
  }

  @Test
  public void testInvalidLogLevels() {
    temporarySystemProperties.set(Property.LOG_LEVEL.getQualifiedName())
        .to("NOPE");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The value 'NOPE' is invalid for the 'jma.log_level' "
        + "property: allowed values are: OFF, ERROR, INFO, WARNING, DEBUG");

    Configuration.Builder.initializeFromSystemProperties(logger).build();
  }

  @Test
  public void testInvalidPercentageUsageThreshold() {
    temporarySystemProperties
        .set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("42.4242%");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Usage thresholds can be specified only to the second "
        + "decimal precision (e.g., 42.42)");

    Configuration.Builder.initializeFromSystemProperties(logger).build();
  }

  @Test
  public void testIncreaseOverTimeUsageThreshold() {
    temporarySystemProperties
        .set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("+90%/3h");

    final Configuration configuration =
        Configuration.Builder.initializeFromSystemProperties(logger).build();
    final IncreaseOverTimeFrameThresholdConfiguration config =
        (IncreaseOverTimeFrameThresholdConfiguration) configuration.getHeapMemoryUsageThreshold();

    assertThat(config.getDelta(), is(90d));
    assertThat(config.getTimeFrame(), is(3d));
    assertThat(config.getTimeUnit(), is(Configuration.IntervalTimeUnit.HOURS));
  }

  @Test
  public void testIncreaseOverTimeUsageThresholdDecimalDelta() {
    temporarySystemProperties
        .set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("+.4%/1s");

    final Configuration configuration =
        Configuration.Builder.initializeFromSystemProperties(logger).build();
    final IncreaseOverTimeFrameThresholdConfiguration config =
        (IncreaseOverTimeFrameThresholdConfiguration) configuration.getHeapMemoryUsageThreshold();

    assertThat(config.getDelta(), is(0.4));
    assertThat(config.getTimeFrame(), is(1d));
    assertThat(config.getTimeUnit(), is(Configuration.IntervalTimeUnit.SECONDS));
  }

  @Test
  public void testInvalidIncreaseOverTimeUsageThresholdMissingDelta() {
    temporarySystemProperties
        .set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("%/1s");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The value '%/1s' is invalid for the "
        + "'jma.thresholds.heap' property: it must follow the Java pattern "
        + "'\\+(\\d*\\.?\\d*\\d)%/(\\d*\\.?\\d*\\d)(ms|s|m|h)'");

    Configuration.Builder.initializeFromSystemProperties(logger).build();
  }

  @Test
  public void testInvalidIncreaseOverTimeUsageThresholdDeltaOnlyPlusPercentage() {
    temporarySystemProperties
        .set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("+%/1s");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The value '+%/1s' is invalid for the "
        + "'jma.thresholds.heap' property: it must follow the Java pattern "
        + "'\\+(\\d*\\.?\\d*\\d)%/(\\d*\\.?\\d*\\d)(ms|s|m|h)'");

    Configuration.Builder.initializeFromSystemProperties(logger).build();
  }

  @Test
  public void testInvalidIncreaseOverTimeUsageThresholdDeltaMissingPercentage() {
    temporarySystemProperties
        .set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("+4/1s");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The value '+4/1s' is invalid for the "
        + "'jma.thresholds.heap' property: it must follow the Java pattern "
        + "'\\+(\\d*\\.?\\d*\\d)%/(\\d*\\.?\\d*\\d)(ms|s|m|h)'");

    Configuration.Builder.initializeFromSystemProperties(logger).build();
  }

  @Test
  public void testInvalidIncreaseOverTimeUsageThresholdDeltaOnlyDot() {
    temporarySystemProperties
        .set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to(".%/1s");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The value '.%/1s' is invalid for the "
        + "'jma.thresholds.heap' property: it must follow the Java pattern "
        + "'\\+(\\d*\\.?\\d*\\d)%/(\\d*\\.?\\d*\\d)(ms|s|m|h)'");

    Configuration.Builder.initializeFromSystemProperties(logger).build();
  }

  @Test
  public void testInvalidIncreaseOverTimeUsageThresholdMissingTimeUnit() {
    temporarySystemProperties
        .set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("+4%/1");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The value '+4%/1' is invalid for the "
        + "'jma.thresholds.heap' property: it must follow the Java pattern "
        + "'\\+(\\d*\\.?\\d*\\d)%/(\\d*\\.?\\d*\\d)(ms|s|m|h)'");

    Configuration.Builder.initializeFromSystemProperties(logger).build();
  }

  @Test
  public void testInvalidIncreaseOverTimeUsageThresholdMissingPlus() {
    temporarySystemProperties
        .set(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName())
        .to("4%/1ms");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The value '4%/1ms' is invalid for the "
        + "'jma.thresholds.heap' property: it must follow the Java pattern "
        + "'\\+(\\d*\\.?\\d*\\d)%/(\\d*\\.?\\d*\\d)(ms|s|m|h)'");

    Configuration.Builder.initializeFromSystemProperties(logger).build();
  }

  @Test
  public void testSystemPropertiesDoNotAffectConfiguration() {
    final Configuration configuration =
        Configuration.Builder.initializeFromSystemProperties(logger).build();

    temporarySystemProperties
        .set(Property.CHECK_INTERVAL.getQualifiedName()).to("42");

    assertThat(configuration.getCheckIntervalInMillis(), is(-1L));
  }

  @Test
  public void testInterval() throws Exception {
    final Configuration configuration = new Configuration();

    Property.CHECK_INTERVAL.doApply(configuration, "42ms");
    assertThat(configuration.getCheckIntervalInMillis(), is(42L));

    Property.CHECK_INTERVAL.doApply(configuration, "42s");
    assertThat(configuration.getCheckIntervalInMillis(), is(42000L));

    Property.CHECK_INTERVAL.doApply(configuration, "2m");
    assertThat(configuration.getCheckIntervalInMillis(), is(120000L));

    Property.CHECK_INTERVAL.doApply(configuration, "3h");
    assertThat(configuration.getCheckIntervalInMillis(), is(10800000L));
  }

  @Test
  public void testMalformedIntervalUnknownTimeUnit() throws Exception {
    temporarySystemProperties
        .set(Property.CHECK_INTERVAL.getQualifiedName())
        .to("42d");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("The value '42d' is invalid for the "
        + "'jma.check_interval' property: it must follow the Java pattern "
        + "'(\\d*\\.?\\d*\\d)(ms|s|m|h)'"));

    Configuration.Builder.initializeFromSystemProperties(logger).build();
  }

  @Test
  public void testMalformedIntervalValueNonPositive() {
    temporarySystemProperties
        .set(Property.CHECK_INTERVAL.getQualifiedName())
        .to("0");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("The value '0' is invalid for the "
        + "'jma.check_interval' property: it must follow the Java pattern "
        + "'(\\d*\\.?\\d*\\d)(ms|s|m|h)'"));

    Configuration.Builder.initializeFromSystemProperties(logger).build();
  }

  @Test
  public void testFrequencyParsing() throws Exception {
    final Configuration.HeapDumpExecutionFrequency s0 =
        Configuration.HeapDumpExecutionFrequency.parse("12/ms");
    assertThat(s0.getExecutionAmount(), is(12));
    assertThat(s0.getTimeFrameInMillis(), is(1L));

    final Configuration.HeapDumpExecutionFrequency s1 =
        Configuration.HeapDumpExecutionFrequency.parse("12/42ms");
    assertThat(s1.getExecutionAmount(), is(12));
    assertThat(s1.getTimeFrameInMillis(), is(42L));

    final Configuration.HeapDumpExecutionFrequency s2 =
        Configuration.HeapDumpExecutionFrequency.parse("6/2s");
    assertThat(s2.getExecutionAmount(), is(6));
    assertThat(s2.getTimeFrameInMillis(), is(2000L));

    final Configuration.HeapDumpExecutionFrequency s3 =
        Configuration.HeapDumpExecutionFrequency.parse("9/4m");
    assertThat(s3.getExecutionAmount(), is(9));
    assertThat(s3.getTimeFrameInMillis(), is(4 * 60 * 1000L));

    final Configuration.HeapDumpExecutionFrequency s4 =
        Configuration.HeapDumpExecutionFrequency.parse("66/19h");
    assertThat(s4.getExecutionAmount(), is(66));
    assertThat(s4.getTimeFrameInMillis(), is(19 * 60 * 60 * 1000L));
  }

}