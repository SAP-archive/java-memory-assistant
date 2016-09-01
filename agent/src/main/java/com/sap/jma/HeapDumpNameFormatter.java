/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HeapDumpNameFormatter {

  private static final Pattern PART_BEGIN_WITH_ESCAPED_PATTERN =
      Pattern.compile("^(?:%%)+[^%]*.*$");
  private static final Pattern SPLIT_PATTERN_IN_PARTS = Pattern.compile(
      // Matches a well-formed, non-token group, e.g., "aabb", "aa%%" or "%%bb"
      "(%%|[^%])+|"
      // Matches a well-formed token, e.g., "%aabb%", "%aa%%%" or "%%%bb%"
      + "%([^%]|%%)+%(?!%)|"
      // Matches a malformed token, e.g., "%aabb", "%aa%%" or "%%%bb"
      + "%([^%]|%%)*$");

  private final List<Part> parts;
  private final String hostName;
  private final Provider<UUID> uuidProvider;

  // VisibleForTesting
  HeapDumpNameFormatter(final String pattern, final String hostName,
                        final Provider<UUID> uuidProvider) {
    this.parts = validateAndSplit(pattern);
    this.hostName = hostName;
    this.uuidProvider = uuidProvider;
  }

  HeapDumpNameFormatter(final String pattern, final String hostName) {
    this(pattern, hostName, new Provider<UUID>() {
      @Override
      public UUID get() {
        return UUID.randomUUID();
      }
    });
  }

  static void validate(final String pattern) throws IllegalArgumentException {
    validateAndSplit(pattern);
  }

  private static List<Part> validateAndSplit(final String pattern) throws IllegalArgumentException {
    if (pattern.length() == 0) {
      throw new IllegalArgumentException("the pattern cannot be empty");
    } else if (pattern.trim().length() == 0) {
      throw new IllegalArgumentException("the pattern cannot be blank");
    }

    // Break in segments and tokens
    final List<Part> parts = new ArrayList<>();
    int index = 0;
    for (final CharSequence part : splitInParts(pattern)) {
      final String escapedPart = part.toString().replaceAll("%%", "%");

      // Check is part is a token
      if (part.charAt(0) == '%' && !PART_BEGIN_WITH_ESCAPED_PATTERN.matcher(part).matches()) {
        /*
         * Check that the token is correctly enveloped in unescaped '%' characters
         */
        if (!TokenType.TOKEN_ESCAPE_PATTERN.matcher(part).matches()) {
          throw new IllegalArgumentException(
              String.format("the token starter character '%%' at position %d does not have a "
                  + "matching token closer '%%' character", index));
        }

        try {
          parts.add(TokenType.instantiate(escapedPart));
        } catch (NoSuchElementException ex) {
          throw new IllegalArgumentException(
              String.format("the token '%s' (position %d to %d) is unknown", part,
                  index, index + part.length() - 1));
        } catch (IllegalTokenConfigurationException ex) {
          throw new IllegalArgumentException(
              String.format("the token '%s' (position %d to %d) has invalid configuration: %s",
                  part, index, index + part.length() - 1, ex.getMessage()));
        } catch (MissingTokenNameException ex) {
          throw new IllegalArgumentException(
              String.format("the name is missing from the token '%s' (position %d to %d)", part,
                  index, index + part.length() - 1));
        } catch (Exception ex) {
          throw new IllegalArgumentException(ex.getMessage());
        }
      } else {
        parts.add(new Part() {
          @Override
          public String format(Object... args) {
            return escapedPart;
          }
        });
      }

      index += part.length();
    }

    return parts;
  }

  // VisibleForTesting
  static List<String> splitInParts(final String pattern) {
    final Matcher matcher = SPLIT_PATTERN_IN_PARTS.matcher(pattern);

    final List<String> res = new ArrayList<>();
    while (matcher.find()) {
      res.add(matcher.group());
    }

    return res;
  }

  public String format(final Date timestamp) {
    final StringBuilder sb = new StringBuilder();
    for (final Part part : parts) {
      if (part instanceof Token) {
        final Token token = (Token) part;
        switch (token.getType()) {
          case HOST_NAME:
            sb.append(part.format(hostName));
            break;
          case RANDOM_UUID:
            sb.append(part.format(uuidProvider.get()));
            break;
          case TIMESTAMP:
            sb.append(part.format(timestamp));
            break;
          case ENVIRONMENT_VARIABLE:
            sb.append(part.format());
            break;
          default:
            throw new IllegalStateException(String.format("Unrecognized token '%s'", part));
        }
      } else {
        sb.append(part.format());
      }
    }

    return sb.toString();
  }

  private enum ConfigurationMode {
    REQUIRED,
    OPTIONAL,
    FORBIDDEN
  }

  private enum TokenType {

    HOST_NAME("host_name", ConfigurationMode.FORBIDDEN),

    ENVIRONMENT_VARIABLE("env", ConfigurationMode.REQUIRED) {
      @Override
      protected void validateConfiguration(final String configuration) {
        /*
         * Every value should be alright. If the environment variable is not set,
         * we will output blank
         */
      }
    },

    TIMESTAMP("ts", ConfigurationMode.OPTIONAL) {
      @Override
      protected void validateConfiguration(final String configuration) {
        new SimpleDateFormat(configuration);
      }
    },

    RANDOM_UUID("uuid", ConfigurationMode.FORBIDDEN);

    /*
     * Pattern that checks if the token is enclosed in non-escaped '%' character
     */
    public static final Pattern TOKEN_ESCAPE_PATTERN = Pattern.compile("%(?:%%|[^%])*%");

    private static final Pattern TOKEN_PATTERN = Pattern.compile("%([^:]*)?[:]?(.*)?%");
    private final String prefix;
    private final ConfigurationMode configurationMode;

    TokenType(final String prefix, final ConfigurationMode configurationMode) {
      this.prefix = prefix;
      this.configurationMode = configurationMode;
    }

    public static TokenType from(final String tokenName) {
      TokenType type = null;
      for (final TokenType t : values()) {
        if (t.prefix.equals(tokenName)) {
          type = t;
          break;
        }
      }

      if (type == null) {
        throw new NoSuchElementException(String.format("the token '%s' is unknown", tokenName));
      }
      return type;
    }

    public static Token instantiate(final String value) {
      final Matcher matcher = TOKEN_PATTERN.matcher(value);

      if (!matcher.matches()) {
        throw new IllegalArgumentException(String.format("Cannot parse token '%s'", value));
      }

      final String tokenName = matcher.group(1);
      if (tokenName.isEmpty()) {
        throw new MissingTokenNameException();
      }

      final String tokenConfiguration = matcher.group(2);
      final boolean hasConfiguration = tokenConfiguration != null && !tokenConfiguration.isEmpty();

      final TokenType tokenType = from(tokenName);

      switch (tokenType.getConfigurationMode()) {
        case REQUIRED: {
          if (!hasConfiguration) {
            throw new IllegalTokenConfigurationException(
                "it requires configuration, but none is provided");
          }

          break;
        }
        case FORBIDDEN: {
          if (hasConfiguration) {
            throw new IllegalTokenConfigurationException(
                String.format("it does not support configuration values provided after the ':' "
                    + "character, but '%s' is provided", tokenConfiguration));
          }

          break;
        }
        default:
          // OPTIONAL
      }

      if (hasConfiguration) {
        try {
          tokenType.validateConfiguration(tokenConfiguration);
        } catch (IllegalArgumentException ex) {
          if (tokenType == TIMESTAMP) {
            // Wrong format
            throw new IllegalTokenConfigurationException(
                String.format("the date formatting pattern '%s' is invalid; for supported "
                    + "patterns, consult the javadoc of %s", tokenConfiguration,
                    SimpleDateFormat.class.getName()));
          }

          throw ex;
        }
      }

      switch (tokenType) {
        case HOST_NAME:
        case RANDOM_UUID:
          return new VerbatimToken(tokenType);
        case ENVIRONMENT_VARIABLE: {
          final String envValue = System.getenv(tokenConfiguration);
          return new AbstractToken(tokenType) {
            final String value = envValue == null ? "" : envValue;

            @Override
            public String format(final Object... args) {
              return value;
            }
          };
        }
        case TIMESTAMP: {
          if (tokenConfiguration == null || tokenConfiguration.trim().isEmpty()) {
            return new AbstractToken(tokenType) {
              @Override
              public String format(final Object... args) {
                return Long.toString(Date.class.cast(args[0]).getTime());
              }
            };
          }

          return new AbstractToken(tokenType) {
            private final SimpleDateFormat dateFormat = new SimpleDateFormat(tokenConfiguration);

            @Override
            public String format(final Object... args) {
              return dateFormat.format((Date) args[0]);
            }
          };
        }
        default:
          throw new IllegalStateException(
              String.format("Unknown token type '%s'", tokenType.name()));
      }
    }

    public String getPrefix() {
      return prefix;
    }

    public ConfigurationMode getConfigurationMode() {
      return configurationMode;
    }

    protected void validateConfiguration(final String configuration) {
      throw new UnsupportedOperationException(
          String.format("The token '%s' does not support configuration", prefix));
    }

  }

  interface Part {

    String format(Object... args);

  }

  interface Token extends Part {

    TokenType getType();

  }

  interface Provider<T> {
    T get();
  }

  private abstract static class AbstractToken implements Token {

    private final TokenType type;

    AbstractToken(final TokenType type) {
      this.type = type;
    }

    @Override
    public TokenType getType() {
      return type;
    }

  }

  private static final class VerbatimToken extends AbstractToken {

    VerbatimToken(final TokenType type) {
      super(type);
    }

    @Override
    public String format(Object... args) {
      return args[0].toString();
    }

  }

  private static class MissingTokenNameException extends RuntimeException {
    private MissingTokenNameException() {
    }
  }

  private static class IllegalTokenConfigurationException extends RuntimeException {
    private IllegalTokenConfigurationException(final String message) {
      super(message);
    }
  }

}