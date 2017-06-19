/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.testapi;

import static com.sap.jma.testapi.Matchers.hasUnexpectedErrors;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

@RunWith(Enclosed.class)
public class MatchersTest {

  public static class HasUnexpectedErrorsTest {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testNoErrors() {
      assertThat("", not(hasUnexpectedErrors()));
    }

    @Test
    public void testOnlyExpectedErrorsSingleAnyMatcher() {
      assertThat("err1\nerr2\rerr3",
          not(hasUnexpectedErrors(anyOf(equalTo("err1"), equalTo("err2"), equalTo("err3")))));
    }

    @Test
    public void testOnlyExpectedErrors() {
      assertThat("err1\nerr2\rerr3",
          not(hasUnexpectedErrors(equalTo("err1"), equalTo("err2"), equalTo("err3"))));
    }

    @Test
    public void testUnexpectedErrorsNoMatchers() {
      assertThat("err", hasUnexpectedErrors());
    }

  }

}
