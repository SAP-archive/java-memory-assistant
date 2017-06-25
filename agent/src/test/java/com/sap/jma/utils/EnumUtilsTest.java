/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.utils;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class EnumUtilsTest {

  @Test
  public void testEmpty() {
    assertThat(EnumUtils.join(new TestEnum[]{}, ","), equalTo(""));
  }

  @Test
  public void testDefaultName() {
    assertThat(EnumUtils.join(TestEnum.values(), ","), equalTo("ONE,TWO,THREE"));
  }

  @Test
  public void testSubset() {
    assertThat(EnumUtils.join(new TestEnum[]{TestEnum.ONE, TestEnum.TWO}, "$"), equalTo("ONE$TWO"));
  }

  @Test
  public void testOverriddenToString() {
    assertThat(EnumUtils.join(TestNamedEnum.values(), ","), equalTo("one,two,three"));
  }

  enum TestEnum {
    ONE, TWO, THREE
  }

  enum TestNamedEnum {
    ONE, TWO, THREE;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

}