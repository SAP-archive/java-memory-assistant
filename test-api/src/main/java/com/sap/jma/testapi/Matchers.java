/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.testapi;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.internal.matchers.ThrowableCauseMatcher;
import org.junit.internal.matchers.ThrowableMessageMatcher;

public final class Matchers {

  private Matchers() {
  }

  public static final class ThrowableMatchers {

    private ThrowableMatchers() {
    }

    public static Matcher<Throwable> hasMessageThat(final Matcher<String> messageMatcher) {
      return new ThrowableMessageMatcher<>(messageMatcher);
    }

    public static Matcher<Throwable> hasCauseThat(final Matcher<? extends Throwable> causeMatcher) {
      return new ThrowableCauseMatcher<>(causeMatcher);
    }

  }

  public static final class StringMatchers {

    private StringMatchers() {
    }

    public static Matcher<String> hasLines(final String... expectedLines) {
      return new TypeSafeMatcher<String>() {

        @Override
        protected boolean matchesSafely(final String item) {
          return Arrays.equals(expectedLines, item.split("\n"));
        }

        @Override
        public void describeTo(final Description description) {
          final List<String> tokens = new LinkedList<>(
              Arrays.asList("is split in the following lines by the '\\n' character:"));
          tokens.addAll(Arrays.asList(expectedLines));
          description.appendText(StringUtils.join(tokens, '\n'));
        }

        @Override
        public void describeMismatchSafely(final String item,
                                           final Description mismatchDescription) {
          final List<String> tokens = new LinkedList<>(
              Arrays.asList("is not split in the following lines by the '\\n' character:"));
          tokens.addAll(Arrays.asList(expectedLines));
          mismatchDescription.appendText(StringUtils.join(tokens, '\n'));
        }

      };
    }

    public static Matcher<String> matchesRegex(final String regex) {
      return new TypeSafeMatcher<String>() {

        @Override
        protected boolean matchesSafely(final String item) {
          return item.matches(regex);
        }

        @Override
        public void describeTo(final Description description) {
          description.appendText("matches the '" + regex + "' regular expression");
        }

        @Override
        public void describeMismatchSafely(final String item,
                                           final Description mismatchDescription) {
          mismatchDescription.appendText("'" + item + "' does not match the '"
              + regex + "' regular expression");
        }

      };
    }

  }

  @SafeVarargs
  public static Matcher<String> hasUnexpectedErrors(
      final Matcher<String> ... expectedErrorMatchers) {
    return hasUnexpectedErrors(Arrays.asList(expectedErrorMatchers));
  }

  public static Matcher<String> hasUnexpectedErrors(
      final List<Matcher<String>> expectedErrorMatchers) {
    return new TypeSafeMatcher<String>() {
      @Override
      protected boolean matchesSafely(final String actual) {
        return getUnexpectedErrors(actual).size() > 0;
      }

      @Override
      public void describeTo(final Description description) {
        if (expectedErrorMatchers.isEmpty()) {
          description.appendText("has no lines in its error output");
          return;
        }

        description.appendText("has lines in its error output that do not match one of:");
        for (final Matcher<String> expectedErrorMatcher : expectedErrorMatchers) {
          expectedErrorMatcher.describeTo(description);
        }
      }

      @Override
      protected void describeMismatchSafely(final String actual,
                                            final Description mismatchDescription) {
        mismatchDescription.appendText("has unexpected errors:");

        for (final String unexpectedError : getUnexpectedErrors(actual)) {
          mismatchDescription.appendText("\n* " + unexpectedError);
        }
      }

      private List<String> getUnexpectedErrors(final String actual) {
        final String[] errorLines = actual.split("[\\n\\r]+");

        if (errorLines.length < 1) {
          return Collections.emptyList();
        }

        final List<String> unexpectedErrors = Lists.newArrayList(errorLines);

        Iterables.removeIf(unexpectedErrors, new Predicate<String>() {
              @Override
              public boolean apply(final String input) {
                return input == null || input.trim().isEmpty();
              }
            });

        Iterables.removeIf(unexpectedErrors, new Predicate<String>() {
          @Override
          public boolean apply(final String actual) {
            for (final Matcher<String> expectedErrorMatcher : expectedErrorMatchers) {
              if (expectedErrorMatcher.matches(actual)) {
                return true;
              }
            }

            return false;
          }
        });

        return unexpectedErrors;
      }

    };
  }

}
