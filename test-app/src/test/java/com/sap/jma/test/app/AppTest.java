/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.test.app;

import static com.sap.jma.test.app.App.Mode.DIRECT_ALLOCATION;
import static com.sap.jma.test.app.App.Mode.STEP_WISE_INCREMENT;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sap.jma.testapi.TemporarySystemProperties;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AppTest {

  public final TemporarySystemProperties systemProperties = TemporarySystemProperties.overlay();

  private static final Matcher<byte[]> arrayWithSize(final int size) {
    return new TypeSafeMatcher<byte[]>() {
      @Override
      protected boolean matchesSafely(byte[] bytes) {
        return bytes.length == size;
      }

      @Override
      protected void describeMismatchSafely(byte[] item, Description mismatchDescription) {
        mismatchDescription.appendText(
            String.format("is expected to be a byte[] with length " + size
                + ", instead it has size " + item.length));
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is a byte array with size " + size);
      }
    };
  }

  @Test
  public void testAppDirectAllocation() throws Exception {
    final ExecutorService executorService = Executors.newSingleThreadExecutor();

    systemProperties.set("jma-test.mode").to(DIRECT_ALLOCATION.name().toLowerCase());
    systemProperties.set("jma-test.allocation").to("5MB");

    final CountDownLatch latch = new CountDownLatch(1);

    final App subject = spy(new App());
    doNothing().when(subject).startManagementInterface();
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocationOnMock) {
        latch.countDown();
        return null;
      }
    }).when(subject).doOnIteration();

    final Future<Void> future = executorService.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        subject.start();
        return null;
      }
    });

    latch.await(3, TimeUnit.SECONDS);
    // Check no error occurred
    future.cancel(true);

    assertThat(subject.hoard, hasSize(1));
    assertThat(subject.hoard.get(0).length, is(5 * 1024 * 1024));

    executorService.shutdownNow();
  }

  @Test
  public void testAppStepWiseIncrement() throws Exception {
    final ExecutorService executorService = Executors.newSingleThreadExecutor();

    systemProperties.set("jma-test.mode").to(STEP_WISE_INCREMENT.name().toLowerCase());
    systemProperties.set("jma-test.allocation").to("10KB");
    systemProperties.set("jma-test.stepPeriodInMillis").to("3");

    final CountDownLatch latch = new CountDownLatch(5);

    final App subject = spy(new App());
    doNothing().when(subject).startManagementInterface();
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocationOnMock) {
        latch.countDown();
        return null;
      }
    }).when(subject).doOnIteration();

    final Future<Void> future = executorService.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        subject.start();
        return null;
      }
    });

    latch.await(1, TimeUnit.MINUTES);
    // Check no error occurred
    future.cancel(true);

    verify(subject, times(5)).doOnIteration();

    assertThat(subject.hoard, hasSize(5));
    assertThat(subject.hoard.get(0), is(arrayWithSize(10 * 1024)));
    assertThat(subject.hoard.get(1), is(arrayWithSize(10 * 1024)));
    assertThat(subject.hoard.get(2), is(arrayWithSize(10 * 1024)));
    assertThat(subject.hoard.get(3), is(arrayWithSize(10 * 1024)));
    assertThat(subject.hoard.get(4), is(arrayWithSize(10 * 1024)));

    executorService.shutdownNow();
  }

}
