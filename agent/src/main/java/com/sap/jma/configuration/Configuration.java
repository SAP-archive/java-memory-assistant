/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.configuration;

import static com.sap.jma.configuration.IntervalTimeUnit.MILLISECONDS;

import com.sap.jma.HeapDumpNameFormatter;
import com.sap.jma.logging.Logger;
import com.sap.jma.vms.MemoryPool.Type;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;

public class Configuration {

  public static final Logger.Severity DEFAULT_LOG_LEVEL = Logger.Severity.ERROR;
  public static final String DEFAULT_NAME_PATTERN =
      "heapdump_%host_name%_%ts:yyyyMMddHHmmss%.hprof";
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
  private ExecutionFrequency maxFrequency;
  private String heapDumpName = DEFAULT_NAME_PATTERN;
  private File heapDumpFolder = new File(System.getProperty("user.dir"));
  private Logger.Severity logLevel = DEFAULT_LOG_LEVEL;
  private IntervalSpecification checkInterval = new IntervalSpecification(-1d, MILLISECONDS);
  private UsageThresholdConfiguration heapMemoryUsageThreshold;
  private UsageThresholdConfiguration codeCacheMemoryUsageThreshold;
  private UsageThresholdConfiguration permGenMemoryUsageThreshold;
  private UsageThresholdConfiguration metaSpaceMemoryUsageThreshold;
  private UsageThresholdConfiguration compressedClassSpaceMemoryUsageThreshold;
  private UsageThresholdConfiguration edenSpaceMemoryUsageThreshold;
  private UsageThresholdConfiguration survivorSpaceMemoryUsageThreshold;
  private UsageThresholdConfiguration oldGenSpaceMemoryUsageThreshold;
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

  public ExecutionFrequency getMaxFrequency() {
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

  public UsageThresholdConfiguration getHeapMemoryUsageThreshold() {
    return heapMemoryUsageThreshold;
  }

  public UsageThresholdConfiguration getCodeCacheMemoryUsageThreshold() {
    return codeCacheMemoryUsageThreshold;
  }

  public UsageThresholdConfiguration getMetaspaceMemoryUsageThreshold() {
    return metaSpaceMemoryUsageThreshold;
  }

  public UsageThresholdConfiguration getPermGenMemoryUsageThreshold() {
    return permGenMemoryUsageThreshold;
  }

  public UsageThresholdConfiguration getCompressedClassSpaceMemoryUsageThreshold() {
    return compressedClassSpaceMemoryUsageThreshold;
  }

  public UsageThresholdConfiguration getEdenSpaceMemoryUsageThreshold() {
    return edenSpaceMemoryUsageThreshold;
  }

  public UsageThresholdConfiguration getSurvivorSpaceMemoryUsageThreshold() {
    return survivorSpaceMemoryUsageThreshold;
  }

  public UsageThresholdConfiguration getOldGenSpaceMemoryUsageThreshold() {
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
        config.maxFrequency = ExecutionFrequency.parse(value);
      }

    },

    HEAP_MEMORY_USAGE_THRESHOLD("thresholds.heap") {
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.heapMemoryUsageThreshold = parseThreshold(Type.HEAP, value);
      }
    },

    CODE_CACHE_MEMORY_USAGE_THRESHOLD("thresholds.code_cache") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.codeCacheMemoryUsageThreshold = parseThreshold(Type.CODE_CACHE, value);
      }
    },

    PERM_GEN_MEMORY_USAGE_THRESHOLD("thresholds.perm_gen") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.permGenMemoryUsageThreshold = parseThreshold(Type.PERM_GEN, value);
      }
    },

    METASPACE_MEMORY_USAGE_THRESHOLD("thresholds.metaspace") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.metaSpaceMemoryUsageThreshold = parseThreshold(Type.METASPACE, value);
      }
    },

    COMPRESSED_CLASS_SPACE_MEMORY_USAGE_THRESHOLD("thresholds.compressed_class") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.compressedClassSpaceMemoryUsageThreshold =
            parseThreshold(Type.COMPRESSED_CLASS_SPACE, value);
      }
    },

    EDEN_SPACE_MEMORY_USAGE_THRESHOLD("thresholds.eden") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.edenSpaceMemoryUsageThreshold = parseThreshold(Type.EDEN_SPACE, value);
      }
    },

    SURVIVOR_SPACE_MEMORY_USAGE_THRESHOLD("thresholds.survivor") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.survivorSpaceMemoryUsageThreshold = parseThreshold(Type.SURVIVOR_SPACE, value);
      }
    },

    OLD_GEN_MEMORY_USAGE_THRESHOLD("thresholds.old_gen") {
      @Override
      void doApply(final Configuration config, final String value)
          throws InvalidPropertyValueException {
        config.oldGenSpaceMemoryUsageThreshold = parseThreshold(Type.OLD_GEN, value);
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

    private static UsageThresholdConfiguration parseThreshold(final Type memoryPool,
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
          return PercentageUsageThresholdConfiguration.parse(memoryPool, value);
        } else if (initialCharacter == '<' || initialCharacter == '=' || initialCharacter == '>') {
          type = "absolute";
          return AbsoluteUsageThresholdConfiguration.parse(memoryPool, value);
        } else {
          type = "increase-over-timeframe";
          return IncreaseOverTimeFrameUsageThresholdConfiguration.parse(memoryPool, value);
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

  public static class Builder {

    private final Logger logger;
    private final Configuration config = new Configuration();

    private Builder(final Logger logger) {
      this.logger = logger;
    }

    public static Builder initializeFromSystemProperties() throws IllegalArgumentException {
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
          if (UsageThresholdConfiguration.class.isAssignableFrom(configField.getType())) {
            try {
              configField.setAccessible(true);

              final IncreaseOverTimeFrameUsageThresholdConfiguration configValue;

              {
                final UsageThresholdConfiguration usageThresholdConfiguration =
                    (UsageThresholdConfiguration) configField.get(config);
                if (!(usageThresholdConfiguration
                    instanceof IncreaseOverTimeFrameUsageThresholdConfiguration)) {
                  continue;
                }

                configValue = IncreaseOverTimeFrameUsageThresholdConfiguration.class
                    .cast(usageThresholdConfiguration);
              }

              final double timeFrame = configValue.getTimeFrame();
              final IntervalTimeUnit timeUnit = configValue.getTimeUnit();
              final long timeFrameInMillis = timeUnit.toMilliSeconds(timeFrame);

              final long checkIntervalInMillis = config.getCheckIntervalInMillis();

              if (checkIntervalInMillis > timeFrameInMillis / 2) {
                warnings.add("the time-frame for the threshold for memory pool '"
                    + configValue.getMemoryPoolType().getDefaultName() + "' of " + timeFrame
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

}
