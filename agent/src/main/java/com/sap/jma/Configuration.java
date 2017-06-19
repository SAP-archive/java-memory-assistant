/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import com.sap.jma.logging.Logger;
import com.sap.jma.vms.JavaVirtualMachine.MemoryPoolType;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Configuration {

  public static final Logger.Severity DEFAULT_LOG_LEVEL = Logger.Severity.ERROR;
  // VisibleForTesting
  static final String DEFAULT_NAME_PATTERN = "heapdump_%host_name%_%ts:yyyyMMddHHmmss%.hprof";
  private static final long DISABLED_INTERVAL = -1L;

  private static final String[] JAVA_MEMORY_ASSISTANT_CONFIGURATIONS_PREFIXES =
      new String[] {"jma.", "hdagent."};
  private static final String OFFICIAL_JAVA_MEMORY_ASSISTANT_CONFIGURATIONS_PREFIX =
      JAVA_MEMORY_ASSISTANT_CONFIGURATIONS_PREFIXES[0];

  private static boolean isAgentConfiguration(final String property) {
    for (final String prefix : JAVA_MEMORY_ASSISTANT_CONFIGURATIONS_PREFIXES) {
      if (property.startsWith(prefix)) {
        return true;
      }
    }

    return false;
  }

  private static String removeConfigurationNamespace(final String property) {
    for (final String prefix : JAVA_MEMORY_ASSISTANT_CONFIGURATIONS_PREFIXES) {
      if (property.startsWith(prefix)) {
        return property.substring(prefix.length());
      }
    }

    throw new IllegalArgumentException(
        String.format("The property '%s' does not begin with a known namespace", property));
  }

  private final List<String> overrides = new LinkedList<>();
  private boolean enabled = false;
  private HeapDumpExecutionFrequency maxFrequency;
  private String heapDumpName = DEFAULT_NAME_PATTERN;
  private File heapDumpFolder = new File(System.getProperty("user.dir"));
  private Logger.Severity logLevel = DEFAULT_LOG_LEVEL;
  private IntervalSpecification checkInterval =
      new IntervalSpecification(-1d, IntervalTimeUnit.MILLISECONDS);
  private ThresholdConfiguration heapMemoryUsageThreshold;
  private ThresholdConfiguration codeCacheMemoryUsageThreshold;
  private ThresholdConfiguration permGenMemoryUsageThreshold;
  private ThresholdConfiguration metaSpaceMemoryUsageThreshold;
  private ThresholdConfiguration compressedClassSpaceMemoryUsageThreshold;
  private ThresholdConfiguration edenSpaceMemoryUsageThreshold;
  private ThresholdConfiguration survivorSpaceMemoryUsageThreshold;
  private ThresholdConfiguration oldGenSpaceMemoryUsageThreshold;
  private String executeBefore;
  private String executeAfter;
  private String executeOnShutDown;
  private String commandInterpreter = System.getProperty("os.name").toLowerCase().startsWith("win")
      ? "cmd.exe" : "/bin/sh";

  // VisibleForTesting
  Configuration() {
  }

  public List<String> getOverrides() {
    return Collections.unmodifiableList(overrides);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public HeapDumpExecutionFrequency getMaxFrequency() {
    return maxFrequency;
  }

  public String getHeapDumpName() {
    return heapDumpName;
  }

  public File getHeapDumpFolder() {
    return heapDumpFolder;
  }

  public Logger.Severity getLogLevel() {
    return logLevel;
  }

  public long getCheckIntervalInMillis() {
    return checkInterval == null ? DISABLED_INTERVAL : checkInterval.toMilliSeconds();
  }

  public ThresholdConfiguration getHeapMemoryUsageThreshold() {
    return heapMemoryUsageThreshold;
  }

  public ThresholdConfiguration getCodeCacheMemoryUsageThreshold() {
    return codeCacheMemoryUsageThreshold;
  }

  public ThresholdConfiguration getMetaspaceMemoryUsageThreshold() {
    return metaSpaceMemoryUsageThreshold;
  }

  public ThresholdConfiguration getPermGenMemoryUsageThreshold() {
    return permGenMemoryUsageThreshold;
  }

  public ThresholdConfiguration getCompressedClassSpaceMemoryUsageThreshold() {
    return compressedClassSpaceMemoryUsageThreshold;
  }

  public ThresholdConfiguration getEdenSpaceMemoryUsageThreshold() {
    return edenSpaceMemoryUsageThreshold;
  }

  public ThresholdConfiguration getSurvivorSpaceMemoryUsageThreshold() {
    return survivorSpaceMemoryUsageThreshold;
  }

  public ThresholdConfiguration getOldGenSpaceMemoryUsageThreshold() {
    return oldGenSpaceMemoryUsageThreshold;
  }

  public String getExecuteBefore() {
    return executeBefore;
  }

  public String getExecuteAfter() {
    return executeAfter;
  }

  public String getExecuteOnShutDown() {
    return executeOnShutDown;
  }

  public String getCommandInterpreter() {
    return commandInterpreter;
  }

  public enum Property {

    ENABLED("enabled") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        validateBoolean(value);
        config.enabled = Boolean.valueOf(value);
      }
    },

    /**
     * The pattern to generate heap-dumps. If no pattern is specified, the following will be used:
     * <p>
     * <code>heapdump-%hostname%-%timestamp%.hprof</code>
     */
    HEAP_DUMP_NAME("heap_dump_name") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        validateHeapDumpName(value);
        config.heapDumpName = value;
      }
    },

    HEAP_DUMP_FOLDER("heap_dump_folder") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        validateHeapDumpFolder(value);
        config.heapDumpFolder = new File(value);
      }
    },

    LOG_LEVEL("log_level") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        try {
          Logger.Severity.valueOf(value.toUpperCase());
        } catch (final IllegalArgumentException ex) {
          final StringBuilder sb = new StringBuilder();
          for (final Logger.Severity s : Logger.Severity.values()) {
            sb.append(s.name());
            sb.append(", ");
          }
          // Remove last ", "
          sb.setLength(sb.length() - 2);

          throw new InvalidPropertyValueException(String.format("allowed values are: %s", sb));
        }

        config.logLevel = Logger.Severity.valueOf(value.toUpperCase());
      }
    },

    CHECK_INTERVAL("check_interval") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        final Matcher matcher = IntervalTimeUnit.INTERVAL_PATTERN.matcher(value);

        if (!matcher.matches()) {
          throw new InvalidPropertyValueException(
              String.format("it must follow the Java pattern '%s'",
                  IntervalTimeUnit.INTERVAL_PATTERN.pattern()));
        }

        final String numberValue = matcher.group(1);
        final double number;
        try {
          number = Double.parseDouble(numberValue);

          if (number < 1) {
            throw new NumberFormatException();
          }
        } catch (final NumberFormatException ex) {
          throw new InvalidPropertyValueException(
              "it must be a positive Java integer (0 < n <= 2147483647)");
        }

        final IntervalTimeUnit timeUnit = IntervalTimeUnit.from(matcher.group(2));

        config.checkInterval = new IntervalSpecification(number, timeUnit);
      }
    },

    MAX_HEAP_DUMP_FREQUENCY("max_frequency") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.maxFrequency = HeapDumpExecutionFrequency.parse(value);
      }

    },

    HEAP_MEMORY_USAGE_THRESHOLD("thresholds.heap") {
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.heapMemoryUsageThreshold =
            parseThreshold(MemoryPoolType.HEAP, value);
      }
    },

    CODE_CACHE_MEMORY_USAGE_THRESHOLD("thresholds.code_cache") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.codeCacheMemoryUsageThreshold =
            parseThreshold(MemoryPoolType.CODE_CACHE, value);
      }
    },

    PERM_GEN_MEMORY_USAGE_THRESHOLD("thresholds.perm_gen") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.permGenMemoryUsageThreshold =
            parseThreshold(MemoryPoolType.PERM_GEN, value);
      }
    },

    METASPACE_MEMORY_USAGE_THRESHOLD("thresholds.metaspace") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.metaSpaceMemoryUsageThreshold =
            parseThreshold(MemoryPoolType.METASPACE, value);
      }
    },

    COMPRESSED_CLASS_SPACE_MEMORY_USAGE_THRESHOLD("thresholds.compressed_class") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.compressedClassSpaceMemoryUsageThreshold =
            parseThreshold(MemoryPoolType.COMPRESSED_CLASS_SPACE, value);
      }
    },

    EDEN_SPACE_MEMORY_USAGE_THRESHOLD("thresholds.eden") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.edenSpaceMemoryUsageThreshold =
            parseThreshold(MemoryPoolType.EDEN_SPACE, value);
      }
    },

    SURVIVOR_SPACE_MEMORY_USAGE_THRESHOLD("thresholds.survivor") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.survivorSpaceMemoryUsageThreshold =
            parseThreshold(MemoryPoolType.SURVIVOR_SPACE, value);
      }
    },

    OLD_GEN_MEMORY_USAGE_THRESHOLD("thresholds.old_gen") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.oldGenSpaceMemoryUsageThreshold =
            parseThreshold(MemoryPoolType.OLD_GEN, value);
      }
    },

    COMMAND_INTERPRETER("command.interpreter") {
      @Override
      void doApply(Configuration config, String value) {
        config.commandInterpreter = value;
      }
    },

    EXECUTE_BEFORE_HEAP_DUMP("execute.before") {
      @Override
      void doApply(Configuration config, String value) {
        config.executeBefore = value;
      }
    },

    EXECUTE_AFTER_HEAP_DUMP("execute.after") {
      @Override
      void doApply(Configuration config, String value) {
        config.executeAfter = value;
      }
    },

    EXECUTE_ON_SHUTDOWN("execute.on_shutdown") {
      @Override
      void doApply(Configuration config, String value) {
        config.executeOnShutDown = value;
      }
    };

    private final String literal;

    Property(final String literal) {
      this.literal = literal;
    }

    private static void validateHeapDumpName(final String value)
        throws InvalidPropertyValueException {
      try {
        HeapDumpNameFormatter.validate(value);
      } catch (final IllegalArgumentException ex) {
        throw new InvalidPropertyValueException(ex.getMessage());
      }
    }

    private static void validateHeapDumpFolder(final String value)
        throws InvalidPropertyValueException {
      final Path path;
      try {
        path = Paths.get(value);
      } catch (final InvalidPathException ex) {
        throw new InvalidPropertyValueException(
            String.format("The heap dump folder path %s is invalid: %s", value, ex.getReason()),
            ex);
      }

      final File heapDumpFolder = path.toFile().getAbsoluteFile();

      if (!heapDumpFolder.exists()) {
        if (!heapDumpFolder.mkdirs()) {
          throw new InvalidPropertyValueException(
              String.format("Cannot create the '%s' directory and one or more of its parents",
                  path.toAbsolutePath()));
        }
      } else if (!heapDumpFolder.isDirectory()) {
        throw new InvalidPropertyValueException(String.format("The file '%s' is not a directory",
            path.toAbsolutePath()));
      }

      // Test by creating and deleting a file
      final File testFile = new File(heapDumpFolder, "test-" + UUID.randomUUID() + ".hprof");

      try {
        if (!testFile.createNewFile()) {
          throw new IOException("Cannot create test file: " + testFile);
        }
      } catch (IOException ex) {
        throw new InvalidPropertyValueException(String.format("Cannot create test file '%s'",
            testFile.toPath().toAbsolutePath()), ex);
      } finally {
        if (testFile.exists() && !testFile.delete()) {
          throw new InvalidPropertyValueException(String.format("Cannot delete test file '%s'",
              testFile.toPath().toAbsolutePath()));
        }
      }
    }

    private static void validateBoolean(final String value) throws InvalidPropertyValueException {
      if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
        throw new InvalidPropertyValueException(
            String.format("'%s' is not a valid boolean",value));
      }
    }

    private static ThresholdConfiguration parseThreshold(final MemoryPoolType memoryPool,
                                                         final String value)
        throws InvalidPropertyValueException {
      final String trimmedValue = value.trim();
      if (trimmedValue.length() < 1) {
        // Disabled
        return null;
      }

      String type = null;
      try {
        final char initialCharacter = trimmedValue.charAt(0);
        if (initialCharacter >= '0' && initialCharacter <= '9') {
          type = "percentage";
          return PercentageThresholdConfiguration.parse(memoryPool, value);
        } else if (initialCharacter == '<' || initialCharacter == '=' || initialCharacter == '>') {
          type = "absolute";
          return AbsoluteThresholdConfiguration.parse(memoryPool, value);
        } else {
          type = "increase-over-timeframe";
          return IncreaseOverTimeFrameThresholdConfiguration.parse(memoryPool, value);
        }
      } catch (final InvalidPropertyValueException ex) {
        throw new InvalidPropertyValueException("cannot parse the value '" + value + "' as "
            + type + " threshold: " + ex.getMessage());
      }
    }

    public static Property from(final String option) {
      if (isAgentConfiguration(option)) {
        final String name = removeConfigurationNamespace(option);
        for (final Property property : Property.values()) {
          if (property.literal.equals(name)) {
            return property;
          }
        }
      }

      throw new NoSuchElementException("The option '" + option + "' is unknown");
    }

    public void apply(final Configuration config, final String value)
        throws InvalidPropertyValueException {
      doApply(config, value);

      /*
       * This will always use the "official" prefix, even though the property has been set via the
       * deprecated namespace
       */
      config.overrides.add(
          String.format("Configuration option '%s' specified with value: '%s'",
          OFFICIAL_JAVA_MEMORY_ASSISTANT_CONFIGURATIONS_PREFIX + this.literal, value));
    }

    // VisibleForTesting
    abstract void doApply(Configuration config, String value) throws InvalidPropertyValueException;

    public String getQualifiedName() {
      return OFFICIAL_JAVA_MEMORY_ASSISTANT_CONFIGURATIONS_PREFIX + literal;
    }

    @Override
    public String toString() {
      return getQualifiedName();
    }

  }

  public enum IntervalTimeUnit {

    MILLISECONDS("ms", 1, TimeUnit.MILLISECONDS),

    SECONDS("s", 1000, TimeUnit.SECONDS),

    MINUTES("m", 1000 * 60, TimeUnit.MINUTES),

    HOURS("h", 1000 * 60 * 60, TimeUnit.HOURS);

    private static final Pattern INTERVAL_PATTERN = Pattern.compile("(\\d*\\.?\\d*\\d)(ms|s|m|h)");

    private final String literal;

    private final int millisMultiplier;

    private final TimeUnit timeUnit;

    IntervalTimeUnit(final String literal, final int millisMultiplier, final TimeUnit timeUnit) {
      this.literal = literal;
      this.millisMultiplier = millisMultiplier;
      this.timeUnit = timeUnit;
    }

    public static IntervalTimeUnit from(final String literal) throws NoSuchElementException {
      for (final IntervalTimeUnit unit : IntervalTimeUnit.values()) {
        if (unit.literal.equals(literal)) {
          return unit;
        }
      }

      throw new NoSuchElementException(String.format("The interval time unit '%s' is unknown",
          literal));
    }

    public static IntervalTimeUnit from(final TimeUnit timeUnit) {
      for (final IntervalTimeUnit itu : IntervalTimeUnit.values()) {
        if (itu.timeUnit == timeUnit) {
          return itu;
        }
      }

      throw new NoSuchElementException(
          String.format("No interval time unit mapped to TimeUnit '%s'", timeUnit.name()));
    }

    public String getLiteral() {
      return literal;
    }

    public double fromMilliseconds(final long timeFrameInMillis) {
      return Math.round(timeFrameInMillis * 100d / millisMultiplier) / 100d;
    }

    public long toMilliSeconds(final double value) {
      return Double.valueOf(Math.floor(value * millisMultiplier)).longValue();
    }

  }

  public interface ThresholdConfiguration {

    MemoryPoolType getMemoryPool();

  }

  static class Builder {

    private final Logger logger;
    private final Configuration config = new Configuration();

    private Builder(final Logger logger) {
      this.logger = logger;
    }

    static Builder initializeFromSystemProperties() throws IllegalArgumentException {
      return initializeFromSystemProperties(Logger.Factory.get(Builder.class));
    }

    // VisibleForTesting
    static Builder initializeFromSystemProperties(final Logger logger)
        throws IllegalArgumentException {
      final Builder builder = new Builder(logger);
      final List<IllegalArgumentException> errors = new ArrayList<>();
      final Set<Entry<Object, Object>> sortedSystemProperties = new TreeSet<>(
          new Comparator<Entry<Object, Object>>() {
          @Override
          public int compare(Entry<Object, Object> e1, Entry<Object, Object> e2) {
            if (e1.getKey() == null) {
              if (e2.getKey() == null) {
                return 0;
              } else {
                return 1;
              }
            }

            if (e2.getKey() == null) {
              return -1;
            }

            return e1.getKey().toString().compareTo(e2.getKey().toString());
          }
        });
      sortedSystemProperties.addAll(System.getProperties().entrySet());

      for (final Entry<Object, Object> entry : sortedSystemProperties) {
        final String key = entry.getKey().toString();

        if (!isAgentConfiguration(key)) {
          continue;
        }

        final String value = entry.getValue().toString().trim();
        if (value.isEmpty()) {
          continue;
        }

        final Property property = Property.from(key);
        try {
          builder.with(property, value);
        } catch (InvalidPropertyValueException ex) {
          errors.add(new IllegalArgumentException(
              String.format("The value '%s' is invalid for the '%s' property: %s", value,
                  property, ex.getMessage())));
        }
      }

      if (!errors.isEmpty()) {
        if (errors.size() == 1) {
          throw errors.get(0);
        }

        final StringBuilder sb = new StringBuilder("There are invalid configuration values:");
        for (final IllegalArgumentException error : errors) {
          sb.append("\n");
          sb.append("* ");
          sb.append(error.getMessage());
        }

        throw new IllegalArgumentException(sb.toString());
      }

      return builder;
    }

    public Builder with(final Property option, final String value)
        throws InvalidPropertyValueException {
      option.apply(config, value);

      return this;
    }

    public Configuration build() {
      validate();

      return config;
    }

    private void validate() {
      if (!config.isEnabled()) {
        return;
      }

      final List<String> errors = new LinkedList<>();
      for (final Entry<Object, Object> entry : new TreeMap<>(System.getProperties()).entrySet()) {
        final String option = entry.getKey().toString().trim();
        if (isAgentConfiguration(option)) {
          try {
            final Property property = Property.from(option);
            final String value = entry.getValue().toString();

            property.apply(config, value);

            logger.debug(String.format("Property '%s' set via environment to: '%s'", property,
                value));
          } catch (final Exception ex) {
            errors.add(ex.getMessage());
          }
        }
      }

      final List<String> warnings = new LinkedList<>();

      /*
       * Issue warning if the 'check_interval' is more then half the 'increase-over-time-frame'
       * check period for any threshold.
       */
      if (config.checkInterval.toMilliSeconds() > 0) {
        for (final Field configField : config.getClass().getDeclaredFields()) {
          if (ThresholdConfiguration.class.isAssignableFrom(configField.getType())) {
            try {
              configField.setAccessible(true);

              final IncreaseOverTimeFrameThresholdConfiguration configValue;

              {
                final ThresholdConfiguration thresholdConfiguration =
                    (ThresholdConfiguration) configField.get(config);
                if (!(thresholdConfiguration
                    instanceof IncreaseOverTimeFrameThresholdConfiguration)) {
                  continue;
                }

                configValue = (IncreaseOverTimeFrameThresholdConfiguration) thresholdConfiguration;
              }

              final double timeFrame = configValue.getTimeFrame();
              final IntervalTimeUnit timeUnit = configValue.getTimeUnit();
              final long timeFrameInMillis = timeUnit.toMilliSeconds(timeFrame);

              final long checkIntervalInMillis = config.getCheckIntervalInMillis();

              if (checkIntervalInMillis > timeFrameInMillis / 2) {
                warnings.add("the time-frame for the threshold for memory pool '"
                    + configValue.getMemoryPool().getDefaultname() + "' of " + timeFrame
                    + timeUnit.getLiteral()
                    + " is too short compared to the overall check-interval of "
                    + checkIntervalInMillis
                    + "ms: to ensure a good precision, the ratio between check-interval and "
                    + "time-frame can be at most 1:2");
              }
            } catch (Exception ex) {
              throw new RuntimeException("Cannot access field '" + configField.getName()
                  + "' from configuration object", ex);
            }
          }
        }
      }

      if (!warnings.isEmpty()) {
        final StringBuilder sb =
            new StringBuilder("The provided configurations have the following issues:");
        for (final String warning : warnings) {
          sb.append("\n* ");
          sb.append(warning);
        }

        logger.warning(sb.toString());
      }

      if (!errors.isEmpty()) {
        final StringBuilder sb =
            new StringBuilder("The provided configurations have the following errors:");
        for (final String error : errors) {
          sb.append("\n* ");
          sb.append(error);
        }

        throw new IllegalArgumentException(sb.toString());
      }
    }

  }

  static class IntervalSpecification {

    private final double value;

    private final IntervalTimeUnit intervalTimeUnit;

    IntervalSpecification(final double value, final IntervalTimeUnit intervalTimeUnit) {
      this.value = value;
      this.intervalTimeUnit = intervalTimeUnit;
    }

    public double getValue() {
      return value;
    }

    public IntervalTimeUnit getIntervalTimeUnit() {
      return intervalTimeUnit;
    }

    public long toMilliSeconds() {
      return intervalTimeUnit.toMilliSeconds(value);
    }
  }

  public enum MemorySizeUnit {

    GIGABYTE("GB", 1024 * 1024 * 1024d),

    MEGABYTE("MB", 1024 * 1024d),

    KILOBYTE("KB", 1024d),

    BYTE("B", 1d);

    private final String literal;
    private final double multiplierToBytes;

    MemorySizeUnit(final String literal, final double multiplierToBytes) {
      this.literal = literal;
      this.multiplierToBytes = multiplierToBytes;
    }

    public static MemorySizeUnit from(final String actual) {
      for (final MemorySizeUnit unit : values()) {
        if (unit.literal.equals(actual.trim())) {
          return unit;
        }
      }

      throw new IllegalArgumentException(
          String.format("Memory size unit '%s' is not recognized; valid values are: %s", actual,
              Configuration.toString(values())));
    }

    public double toBytes(double valueInUnitSize) {
      return valueInUnitSize * multiplierToBytes;
    }

    public String getLiteral() {
      return literal;
    }

  }

  public enum Comparison {
    SMALLER_THAN("<") {
      @Override
      public boolean compare(double expected, double actual) {
        return expected < actual;
      }
    },
    SMALLER_THAN_OR_EQUAL_TO("<=") {
      @Override
      public boolean compare(double expected, double actual) {
        return expected <= actual;
      }
    },
    EQUAL_TO("==") {
      @Override
      public boolean compare(double expected, double actual) {
        return expected == actual;
      }
    },
    LARGER_THAN(">") {
      @Override
      public boolean compare(double expected, double actual) {
        return expected > actual;
      }
    },
    LARGER_THAN_OR_EQUAL_TO(">=") {
      @Override
      public boolean compare(double expected, double actual) {
        return expected >= actual;
      }
    };

    private final String literal;

    Comparison(final String literal) {
      this.literal = literal;
    }

    public abstract boolean compare(final double expected, final double actual);

    public static Comparison from(final String actual) {
      for (final Comparison comparison : values()) {
        if (comparison.literal.equals(actual.trim())) {
          return comparison;
        }
      }

      throw new IllegalArgumentException(
          String.format("Comparison operator '%s' is not recognized; valid values are: %s",
              actual, Configuration.toString(values())));
    }

  }

  public static class AbsoluteThresholdConfiguration implements ThresholdConfiguration {

    private static final Pattern ABSOLUTE_PATTERN =
        Pattern.compile("([<=>]+)(\\d*\\.?\\d*\\d)([KMG]?B)");

    static AbsoluteThresholdConfiguration parse(final MemoryPoolType memoryPoolType,
                                                final String value)
        throws InvalidPropertyValueException {
      final Matcher matcher = ABSOLUTE_PATTERN.matcher(value);

      if (!matcher.matches()) {
        throw new InvalidPropertyValueException(
            String.format("it must follow the Java pattern '%s'", ABSOLUTE_PATTERN.pattern()));
      }

      try {
        final Comparison comparison = Comparison.from(matcher.group(1));
        final double valueInUnitSize = Double.parseDouble(matcher.group(2));
        final MemorySizeUnit memorySizeUnit = MemorySizeUnit.from(matcher.group(3));

        final double valueInBytes = memorySizeUnit.toBytes(valueInUnitSize);

        return new AbsoluteThresholdConfiguration(memoryPoolType, comparison, valueInBytes,
            memorySizeUnit, value);
      } catch (final Exception ex) {
        throw new InvalidPropertyValueException("cannot be parsed", ex);
      }
    }

    private final MemoryPoolType memoryPool;
    private final Comparison comparison;
    private final double targetValueInBytes;
    private final MemorySizeUnit memorySizeUnit;
    private final String configurationValue;

    AbsoluteThresholdConfiguration(final MemoryPoolType memoryPool,
                                   final Comparison comparison,
                                   final double targetValueInBytes,
                                   final MemorySizeUnit memorySizeUnit,
                                   final String configurationValue) {
      this.memoryPool = memoryPool;
      this.comparison = comparison;
      this.targetValueInBytes = targetValueInBytes;
      this.memorySizeUnit = memorySizeUnit;
      this.configurationValue = configurationValue;
    }

    public Comparison getComparison() {
      return comparison;
    }

    public double getTargetValueInBytes() {
      return targetValueInBytes;
    }

    public MemorySizeUnit getMemorySizeUnit() {
      return memorySizeUnit;
    }

    @Override
    public MemoryPoolType getMemoryPool() {
      return memoryPool;
    }

    @Override
    public String toString() {
      return memoryPool + " " + configurationValue;
    }
  }

  public static class PercentageThresholdConfiguration implements ThresholdConfiguration {

    private static final Pattern PERCENTAGE_PATTERN = Pattern.compile("(\\d*\\.?\\d*\\d)%");
    private final double value;
    private final MemoryPoolType memoryPool;

    PercentageThresholdConfiguration(final MemoryPoolType memoryPool, final double value) {
      this.memoryPool = memoryPool;
      this.value = value;
    }

    static PercentageThresholdConfiguration parse(final MemoryPoolType memoryPoolType,
                                                  final String value)
        throws InvalidPropertyValueException {
      final Matcher matcher = PERCENTAGE_PATTERN.matcher(value);

      if (!matcher.matches()) {
        throw new InvalidPropertyValueException(
            String.format("it must follow the Java pattern '%s'", PERCENTAGE_PATTERN.pattern()));
      }

      final String valueString = matcher.group(1);
      if (valueString.isEmpty()) {
        throw new InvalidPropertyValueException(
            String.format("it must follow the Java pattern '%s' and have at least "
                + "a digit before the '%%' sign", PERCENTAGE_PATTERN.pattern()));
      }

      final double f = Double.parseDouble(valueString);
      if (f < 0d || f > 100d) {
        throw new InvalidPropertyValueException(
            String.format("Usage threshold must be between 0f and 100f"),
            new NumberFormatException());
      }

      final BigDecimal bd = new BigDecimal(valueString);
      final int scale = bd.scale();
      if (scale > 2) {
        throw new InvalidPropertyValueException(
            String.format("Usage thresholds can be specified only to the second "
                + "decimal precision (e.g., 42.42)"), new NumberFormatException());
      }

      return new PercentageThresholdConfiguration(memoryPoolType, f);
    }

    @Override
    public MemoryPoolType getMemoryPool() {
      return memoryPool;
    }

    public double getValue() {
      return value;
    }

  }

  /*
   * TODO Refactor to unify with HeapDumpExecutionFrequency?
   */
  public static class IncreaseOverTimeFrameThresholdConfiguration
      implements ThresholdConfiguration {

    private static final Pattern INCREASE_OVER_TIME_FRAME_PATTERN =
        Pattern.compile("\\+(\\d*\\.?\\d*\\d)%/(\\d*\\.?\\d*\\d)(ms|s|m|h)");
    private final MemoryPoolType memoryPool;
    private final double delta;
    private final double timeFrame;
    private final IntervalTimeUnit timeUnit;

    IncreaseOverTimeFrameThresholdConfiguration(final MemoryPoolType memoryPool,
                                                final double delta,
                                                final double timeFrame,
                                                final IntervalTimeUnit timeUnit) {
      this.memoryPool = memoryPool;
      this.delta = delta;
      this.timeFrame = timeFrame;
      this.timeUnit = timeUnit;
    }

    public static IncreaseOverTimeFrameThresholdConfiguration parse(
        final MemoryPoolType memoryPool,
        final String value)
        throws InvalidPropertyValueException {
      final Matcher matcher = INCREASE_OVER_TIME_FRAME_PATTERN.matcher(value);

      if (!matcher.matches()) {
        throw new InvalidPropertyValueException(
            String.format("it must follow the Java pattern '%s'",
                INCREASE_OVER_TIME_FRAME_PATTERN.pattern()));
      }

      final String deltaString = matcher.group(1);
      if (deltaString.isEmpty()) {
        throw new InvalidPropertyValueException(
            String.format("it must follow the Java pattern '%s' and have at "
                + "least a digit before the '%%' sign",
                INCREASE_OVER_TIME_FRAME_PATTERN.pattern()));
      }

      final double delta;
      try {
        delta = Double.parseDouble(deltaString);

        if (delta <= 0) {
          throw new NumberFormatException();
        }
      } catch (final NumberFormatException ex) {
        throw new InvalidPropertyValueException(
            String.format("The value '%s' is not valid for the increase on memory usage in "
                + "the time-frame: must be a positive Java double (0 < n <= %.2f)",
                matcher.group(1), Double.MAX_VALUE), new NumberFormatException());
      }

      final String timeFrameValue = matcher.group(2);
      final double timeFrameInt;
      if (timeFrameValue.isEmpty()) {
        timeFrameInt = 1;
      } else {
        try {
          timeFrameInt = Double.parseDouble(timeFrameValue);

          if (timeFrameInt < 1) {
            throw new NumberFormatException();
          }
        } catch (final NumberFormatException ex) {
          throw new InvalidPropertyValueException(
              String.format("The value '%s' is not valid for the time-frame of memory usage "
                  + "increase threshold: must be a positive Java integer (0 < n <= 2147483647)",
                  timeFrameValue), new NumberFormatException());
        }
      }

      final IntervalTimeUnit timeFrameUnit;
      try {
        timeFrameUnit = IntervalTimeUnit.from(matcher.group(3));
      } catch (final NoSuchElementException ex) {
        final StringBuilder values = new StringBuilder();
        for (final IntervalTimeUnit unit : IntervalTimeUnit.values()) {
          values.append(unit.literal);
          values.append(',');
          values.append(' ');
        }
        // Drop last ", "
        values.setLength(values.length() - 2);

        throw new InvalidPropertyValueException(
            String.format("The value '%s' is not valid for the time unit of "
                + "the time-frame of memory usage increase threshold: valid values are "
                + values, timeFrameValue));
      }

      return new IncreaseOverTimeFrameThresholdConfiguration(memoryPool, delta, timeFrameInt,
          timeFrameUnit);
    }

    @Override
    public MemoryPoolType getMemoryPool() {
      return memoryPool;
    }

    public double getDelta() {
      return delta;
    }

    public double getTimeFrame() {
      return timeFrame;
    }

    public IntervalTimeUnit getTimeUnit() {
      return timeUnit;
    }
  }

  static final class HeapDumpExecutionFrequency {

    private static final Pattern EXECUTION_FREQUENCY_PATTERN =
        Pattern.compile("(\\d+)/(\\d*)(ms|s|m|h)");

    private final int executionAmount;
    private final long timeFrameInMillis;
    protected final String spec;

    HeapDumpExecutionFrequency(final int executionAmount, final long timeFrameInMillis,
                               final String spec) {
      this.executionAmount = executionAmount;
      this.timeFrameInMillis = timeFrameInMillis;
      this.spec = spec;
    }

    public int getExecutionAmount() {
      return executionAmount;
    }

    public long getTimeFrameInMillis() {
      return timeFrameInMillis;
    }

    public static HeapDumpExecutionFrequency parse(final String value)
        throws InvalidPropertyValueException {
      final Matcher matcher = EXECUTION_FREQUENCY_PATTERN.matcher(value);

      if (!matcher.matches()) {
        throw new InvalidPropertyValueException(
            String.format("it must follow the Java pattern '%s'",
                EXECUTION_FREQUENCY_PATTERN.pattern()));
      }

      final int maxCount;
      try {
        maxCount = Integer.parseInt(matcher.group(1));

        if (maxCount < 1) {
          throw new NumberFormatException();
        }
      } catch (final NumberFormatException ex) {
        throw new InvalidPropertyValueException(
            String.format("The value '%s' is not valid for the max amount of heap dumps "
                + "in a time-frame: must be a positive Java integer (0 < n <= 2147483647)",
                matcher.group(1)));
      }

      final String timeFrameValue = matcher.group(2);
      final int timeFrameInt;
      if (timeFrameValue.isEmpty()) {
        timeFrameInt = 1;
      } else {
        try {
          timeFrameInt = Integer.parseInt(timeFrameValue);

          if (timeFrameInt < 1) {
            throw new NumberFormatException();
          }
        } catch (final NumberFormatException ex) {
          throw new InvalidPropertyValueException(
              String.format("The value '%s' is not valid for the time-frame of heap dumps: "
                  + "must be a positive Java integer (0 < n <= 2147483647)", timeFrameValue));
        }
      }

      final IntervalTimeUnit timeFrameUnit;
      try {
        timeFrameUnit = IntervalTimeUnit.from(matcher.group(3));
      } catch (final NoSuchElementException ex) {
        final StringBuilder values = new StringBuilder();
        for (final IntervalTimeUnit unit : IntervalTimeUnit.values()) {
          values.append(unit.literal);
          values.append(',');
          values.append(' ');
        }
        // Drop last ", "
        values.setLength(values.length() - 2);

        throw new InvalidPropertyValueException(
            String.format("The value '%s' is not valid for the time unit of the time-frame "
                + "of heap dumps: valid values are " + values, timeFrameValue));
      }

      return new HeapDumpExecutionFrequency(maxCount,
          timeFrameUnit.toMilliSeconds(timeFrameInt), value);
    }

    // Side-effects. Ugly, but on JVM 7 every other option is uglier. Streams, we miss you!
    void filterToRelevantEntries(final List<Date> executionHistory, final Date now) {
      final Date latestRelevantTimestamp = getEarliestRelevantTimestamp(now);

      final Iterator<Date> i = executionHistory.iterator();
      while (i.hasNext()) {
        final Date executionTimestamp = i.next();
        if (executionTimestamp.before(latestRelevantTimestamp)) {
          i.remove();
        }
      }

      Collections.sort(executionHistory);
    }

    boolean canPerformExecution(final List<Date> executionHistory,
                                       final Date newExecutionTime) {
      final Date latestRelevantTimestamp = getEarliestRelevantTimestamp(newExecutionTime);

      int count = 0;
      for (final Date executionTimestamp : executionHistory) {
        if (latestRelevantTimestamp.before(executionTimestamp)) {
          count += 1;
        }
      }

      return count < executionAmount;
    }

    private Date getEarliestRelevantTimestamp(Date now) {
      return new Date(now.getTime() - timeFrameInMillis);
    }

  }

  static class InvalidPropertyValueException extends Exception {

    private InvalidPropertyValueException(String message) {
      super(message);
    }

    private InvalidPropertyValueException(String message, Throwable cause) {
      super(message, cause);
    }

  }

  private static String toString(final Enum<? extends Enum>[] array) {
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
