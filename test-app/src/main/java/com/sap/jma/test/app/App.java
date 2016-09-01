/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.test.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {

  private static final Pattern MEMORY_SIZE_PATTERN =
      Pattern.compile("(\\d+)([GMK]B)", Pattern.CASE_INSENSITIVE);
  List<byte[]> hoard;

  // VisibleForTesting
  App() {
  }

  public static void main(final String[] args) throws Exception {
    if (args.length > 0) {
      throw new IllegalArgumentException("No app arguments accepted; use System properties");
    }

    new App().start();
  }

  private static int toBytes(final String memorySize) {
    final Matcher matcher = MEMORY_SIZE_PATTERN.matcher(memorySize);

    if (!matcher.matches()) {
      throw new IllegalArgumentException("The memory size '" + memorySize
          + "' does not match the MEMORY_SIZE_PATTERN '" + MEMORY_SIZE_PATTERN.pattern() + "'");
    }

    final int pow;
    switch (matcher.group(2).toUpperCase()) {
      case "GB":
        pow = 1024 * 1024 * 1024;
        break;
      case "MB":
        pow = 1024 * 1024;
        break;
      case "KB":
        pow = 1024;
        break;
      default:
        throw new IllegalArgumentException("Invalid memory size unit: " + matcher.group(2));
    }

    return Integer.parseInt(matcher.group(1)) * pow;
  }

  // VisibleForTesting
  void doOnIteration() {
  }

  // VisibleForTesting
  void start() throws Exception {
    startManagementInterface();

    final boolean log = Boolean.getBoolean(System.getProperty("jma-test.log", "false"));
    final long stepPeriod = Long.parseLong(System.getProperty("jma-test.stepPeriod", "5000"));
    final int allocation = toBytes(System.getProperty("jma-test.allocation", "300MB"));

    final Mode mode = Mode.valueOf(System.getProperty("jma-test.mode").toUpperCase());

    if (log) {
      System.out.println(String.format("TestApp started in '%s' mode", mode));

      System.out.println("System Properties:");
      for (final Map.Entry<Object, Object> systemProperty : System.getProperties().entrySet()) {
        System.out.println("* '" + systemProperty.getKey()
            + "' => '" + systemProperty.getValue() + "'");
      }
    }

    try {
      switch (mode) {
        case DIRECT_ALLOCATION:
          directAllocation(log, allocation, stepPeriod);
          return;
        case STEP_WISE_INCREMENT:
          stepWiseIncrement(log, allocation, stepPeriod);
          return;
        default:
          throw new IllegalStateException("Unrecognized mode: "
              + System.getProperty("jma-test.mode"));
      }
    } catch (final InterruptedException ex) {
      if (log) {
        System.out.println("App terminated via interruption");
      }
    }
  }

  // VisibleForTesting
  void startManagementInterface() throws Exception {
    final ServerSocket serverSocket = new ServerSocket(12345);
    // Block until someone connects, then handle chatter on dedicated thread
    final Socket socket = serverSocket.accept();

    System.out.println("Management interface connected: " + socket);

    final Runnable closeSockets = new Runnable() {
      @Override
      public void run() {
        try {
          if (!socket.isClosed()) {
            socket.close();
          }
        } catch (final IOException ex) {
          System.err.println("Error while closing the management interface socket");
          ex.printStackTrace(System.err);
        } finally {
          try {
            if (!serverSocket.isClosed()) {
              serverSocket.close();
            }
          } catch (final IOException ex) {
            System.err.println("Error while closing the management interface server socket");
            ex.printStackTrace(System.err);
          }
        }
      }
    };

    Runtime.getRuntime().addShutdownHook(new Thread("Cleanup") {
      @Override
      public void run() {
        closeSockets.run();
      }
    });

    new Thread("ManagementInterface") {
      @Override
      public void run() {
        try (final BufferedReader commands =
                 new BufferedReader(new InputStreamReader(socket.getInputStream(),
                     StandardCharsets.UTF_8))) {
          String command;
          while ((command = commands.readLine()) != null) {
            switch (command) {
              case "SHUTDOWN": {
                return;
              }
              default: {
                System.out.println(String.format("Unrecognized command: '%s'", command));
              }
            }
          }
        } catch (final Exception ex) {
          if (!socket.isClosed()) {
            System.err.println("Error while reading from the management interface socket");
            ex.printStackTrace(System.err);
          }
        } finally {
          try {
            closeSockets.run();
          } finally {
            System.exit(0);
          }
        }
      }
    }.start();
  }

  private void directAllocation(final boolean log, final int allocationInBytes,
                                final long stepPeriod) throws InterruptedException {
    hoard = Collections.singletonList(new byte[Math.min(Integer.MAX_VALUE, allocationInBytes)]);

    if (log) {
      System.out.println("Allocated " + hoard.get(0).length + " contiguous bytes");
    }

    do {
      Thread.sleep(stepPeriod);
      doOnIteration();
    } while (true);
  }

  private void stepWiseIncrement(final boolean log, final int incrementInBytes,
                                 final long stepPeriod) throws InterruptedException {
    hoard = new ArrayList<>();
    int totalSize = 0;
    while (true) {
      Thread.sleep(stepPeriod);

      hoard.add(new byte[incrementInBytes]);
      totalSize += incrementInBytes;

      if (log) {
        System.out.println("Allocating " + incrementInBytes + " pointers; total: "
            + totalSize + " bytes");
      }

      doOnIteration();
    }
  }

  enum Mode {
    DIRECT_ALLOCATION,
    STEP_WISE_INCREMENT
  }

}