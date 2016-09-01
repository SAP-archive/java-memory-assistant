/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.testapi;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TemporarySystemPropertiesTest {

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  private TemporarySystemProperties subject;

  @Before
  public void setup() {
    subject = TemporarySystemProperties.overlay();
  }

  @Test
  public void testSetup() {
    assertThat(System.getProperty("test1"), nullValue());
    assertThat(System.getProperty("test2"), nullValue());

    subject.before();

    subject.set("test1").to("ciao1");
    subject.set("test2").to("ciao2");

    assertThat(System.getProperty("test1"), is("ciao1"));
    assertThat(System.getProperty("test2"), is("ciao2"));

    subject.after();

    assertThat(System.getProperty("test1"), nullValue());
    assertThat(System.getProperty("test2"), nullValue());
    assertThat(subject.originalValues.size(), is(0));
  }

  @Test
  public void testIncompleteSetupAtEndOfTest() {
    subject.before();

    subject.set("test1");

    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage(is(
        "The TemporarySystemPropertySetter for key 'test1' has not been finalized yet with "
        + "the call TemporarySystemPropertySetter.to(value)"));

    subject.after();
  }

  @Test
  public void testIncompleteSetupWithAnotherSet() {
    subject.set("test1");

    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage(is(
        "The TemporarySystemPropertySetter for key 'test1' has not been finalized yet with "
        + "the call TemporarySystemPropertySetter.to(value)"));

    subject.set("test2").to("ciao2");
  }

  @Test
  public void testNullKey() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("The provided key is null"));

    subject.set(null);
  }

}
