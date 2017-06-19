/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.testapi.process;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public interface ProcessCondition {

  void run(Process process) throws Exception;

  class Factory {

    private Factory() {
    }

    public static ProcessCondition timeElapses(final long timeout,
                                               final TimeUnit timeUnit) {
      return new ProcessCondition() {

        private final String description =
            String.format("%d %s elapse", timeout, timeUnit);

        @Override
        public void run(final Process process) {
          final long start = System.currentTimeMillis();
          final long end = start + timeUnit.toMillis(timeout);

          while (System.currentTimeMillis() < end) {
            try {
              Thread.sleep(end - System.currentTimeMillis());
            } catch (final InterruptedException ex) {
              // Nevermind
            }
          }
        }

        @Override
        public String toString() {
          return description;
        }

      };
    }

    public static ProcessCondition fileCreatedIn(final String fileNameRegex,
                                                 final File targetFolder,
                                                 final long timeout,
                                                 final TimeUnit timeUnit) {
      return new ProcessCondition() {

        private final String description =
            String.format("File matching name pattern '%s' found in targetFolder '%s'",
                fileNameRegex, targetFolder);

        @Override
        public void run(final Process process) {
          final long start = System.currentTimeMillis();
          final long end = start + timeUnit.toMillis(timeout);

          final Pattern pattern = Pattern.compile(fileNameRegex);
          final Predicate<String> matcher = new Predicate<String>() {
            @Override
            public boolean apply(final String file) {
              return pattern.matcher(file).matches();
            }
          };

          while (!Iterables.any(Arrays.asList(targetFolder.list()), matcher)) {
            try {
              Thread.sleep(100L);
            } catch (final Exception ex) {
              throw new RuntimeException(ex);
            }

            if (System.currentTimeMillis() > end) {
              throw new RuntimeException(new TimeoutException());
            }
          }
        }

        @Override
        public String toString() {
          return description;
        }

      };
    }

    public static ProcessCondition heapDumpCreatedIn(final File heapDumpFolder,
                                                     final long timeout,
                                                     final TimeUnit timeUnit) {
      return fileCreatedIn("^.*\\.hprof$", heapDumpFolder, timeout, timeUnit);
    }

  }

}
