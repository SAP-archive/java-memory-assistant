/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.concurrent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadFactories {

  /**
   * The default prefix of the name given to threads created by the return
   * value of {@link #deamons()}.
   */
  @SuppressWarnings("nls")
  private static final String DEFAULT_THREAD_NAME = "Thread";
  /**
   * All thread names created by the factory will be suffixed with "Daemon".
   */
  @SuppressWarnings("nls")
  private static final String DAEMON_SUFFIX = "Daemon";

  private static final ConcurrentMap<String, NamedThreadFactory> NAMED_DEAMON_THREAD_FACTORIES
      = new ConcurrentHashMap<String, NamedThreadFactory>();

  private ThreadFactories() {
  }

  /**
   * @return A {@link ThreadFactory} that creates deamon {@link Thread} with
   * name {@code Thread-[index]}, where {@code index} is a number
   * starting from one and increasing with every invocation of
   * {@link ThreadFactory#newThread(Runnable)}. If this method is
   * invoked multiple, the same {@link ThreadFactory} instance is
   * returned.
   */
  public static ThreadFactory deamons() {
    return ThreadFactories.deamons(DEFAULT_THREAD_NAME);
  }

  /**
   * @return A {@link ThreadFactory} that creates deamon {@link Thread} with
   * name {@code [threadName]-[index]}, where {@code index} is a
   * number starting from one and increasing with every invocation of
   * {@link ThreadFactory#newThread(Runnable)}. If this method is
   * invoked multiple times with the same {@code threadName}, the same
   * {@link ThreadFactory} instance is returned.
   */
  public static ThreadFactory deamons(final String threadName) {
    final NamedThreadFactory tmp = new NamedThreadFactory(threadName);

    if (NAMED_DEAMON_THREAD_FACTORIES.putIfAbsent(threadName, tmp) == null) {
      /*
       * Throw-away factory actually used and cached
       */
      return tmp;
    }

    return NAMED_DEAMON_THREAD_FACTORIES.get(threadName);
  }

  static void clear() {
    NAMED_DEAMON_THREAD_FACTORIES.clear();
  }

  private static final class NamedThreadFactory implements ThreadFactory {

    private final String threadName;

    private final AtomicInteger index = new AtomicInteger(0);

    private NamedThreadFactory(final String threadName) {
      this.threadName = threadName;
    }

    @Override
    public Thread newThread(final Runnable runnable) {
      final Thread t = new Thread(runnable);

      t.setName(String.format("%s-%s-%s", threadName, index.incrementAndGet(), DAEMON_SUFFIX));
      t.setDaemon(true);
      return t;
    }
  }

}
