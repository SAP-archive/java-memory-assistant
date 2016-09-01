/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.concurrent;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ThreadFactoriesTest {
  @Before
  public void setup() {
    ThreadFactories.clear();
  }

  @Test
  public void testDefaultDeamonThreadFactory() throws Exception {
    final ThreadFactory deamons = ThreadFactories.deamons();
    assertThat(deamons, sameInstance(ThreadFactories.deamons()));
    assertThat(deamons.newThread(mock(Runnable.class)).getName(), equalTo("Thread-1-Daemon"));
    assertThat(deamons.newThread(mock(Runnable.class)).getName(), equalTo("Thread-2-Daemon"));
    assertThat(deamons.newThread(mock(Runnable.class)).getName(), equalTo("Thread-3-Daemon"));
    testDeamonThreadFactory(deamons);
  }

  @Test
  public void testNamedDeamonThreadFactory() throws Exception {
    final ThreadFactory deamons = ThreadFactories.deamons("Test");
    assertThat(deamons, sameInstance(ThreadFactories.deamons("Test")));
    assertThat(deamons.newThread(mock(Runnable.class)).getName(), equalTo("Test-1-Daemon"));
    assertThat(deamons.newThread(mock(Runnable.class)).getName(), equalTo("Test-2-Daemon"));
    assertThat(deamons.newThread(mock(Runnable.class)).getName(), equalTo("Test-3-Daemon"));
    testDeamonThreadFactory(deamons);
  }

  private void testDeamonThreadFactory(final ThreadFactory deamons) throws InterruptedException {
    /*
     * Test that the runnable is actually executed
     */
    final Runnable mock = mock(Runnable.class);
    final CountDownLatch latch = new CountDownLatch(1);
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(final InvocationOnMock invocation) throws Throwable {
        latch.countDown();
        return null;
      }
    }).when(mock).run();
    final Thread t = deamons.newThread(mock);
    assertThat(t.isDaemon(), is(true));
    t.start();

    assertTrue(latch.await(5, TimeUnit.SECONDS));
  }
}
