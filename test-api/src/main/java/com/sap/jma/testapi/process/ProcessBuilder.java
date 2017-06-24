/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.testapi.process;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;

public class ProcessBuilder {

  private final Set<String> jvmArguments = new LinkedHashSet<>();

  private final Map<String, String> environmentProperties = new LinkedHashMap<>();
  private final Map<String, String> systemProperties = new LinkedHashMap<>();
  private final String jvmName;
  private final String javaPath;
  private final File jarsDir;
  private final String version;

  public ProcessBuilder(final String jvmName, final String javaPath, final File jarsDir,
                        final String version, final File dumpsDir) {
    this.jvmName = jvmName;
    this.javaPath = javaPath;
    this.jarsDir = jarsDir;
    this.version = version;

    withJvmArgument("-XX:+HeapDumpOnOutOfMemoryError");
    withJvmArgument("-XX:HeapDumpPath=" + dumpsDir + "/" + jvmName + "_pid_"
        + UUID.randomUUID() + ".hprof");
  }

  public ProcessBuilder withEnvironmentProperty(final String key, final String value) {
    environmentProperties.put(key, value);

    return this;
  }

  public ProcessBuilder withJvmArgument(final String value) {
    jvmArguments.add(value);

    return this;
  }

  public ProcessBuilder withSystemProperty(final String propertyName, final String value) {
    systemProperties.put(propertyName, value);

    return this;
  }

  public Process buildAndRunUntil(final ProcessCondition processCondition) throws Exception {
    return buildAndRunUntil(processCondition, true);
  }

  public Process buildAndRunUntil(final ProcessCondition processCondition,
                                  final boolean killAfterConditionMet) throws Exception {
    final Process process = build();
    try {
      processCondition.run(process);
      return process;
    } catch (InterruptedException ex) {
      System.out.println(String.format("Process condition '%s' interrupted", processCondition));
      throw ex;
    } finally {
      if (killAfterConditionMet) {
        process.shutdown();
      }

      System.out.println("Process output:\n" + process.getOut());
      System.out.println("Process error:\n" + process.getErr());
    }
  }

  public Process build() throws Exception {
    final List<String> arguments = new LinkedList<>();

    arguments.add(javaPath);
    arguments.add("-javaagent:" + jarsDir + "/java-memory-assistant-" + version + ".jar");

    arguments.addAll(jvmArguments);

    for (final Map.Entry<String, String> systemProperty : systemProperties.entrySet()) {
      arguments.add("-D" + systemProperty.getKey() + (systemProperty.getValue() == null ? "" :
          "=" + systemProperty.getValue()));
    }

    arguments.add("-jar");
    arguments.add(jarsDir + "/test-app-" + version + ".jar");

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();

    final StartedProcess startedProcess = new ProcessExecutor() //
        .environment(environmentProperties) //
        .command(arguments) //
        .readOutput(true) //
        .redirectOutput(out) //
        .redirectError(err) //
        .start();

    /*
     * Connect to the management interface, which also gives us the guarantee
     * that, if we succeed here, the TestApp is up and running
     */
    final Socket socket = connectToManagementApi(20, TimeUnit.SECONDS);

    return new Process() {

      private void closeConnection() {
        try {
          if (!socket.isClosed()) {
            socket.close();
          }
        } catch (final Exception ex) {
          // Nevermind
        }
      }

      @Override
      public String getOut() {
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
      }

      @Override
      public String getErr() {
        return new String(err.toByteArray(), StandardCharsets.UTF_8);
      }

      @Override
      public void destroy() throws Exception {
        try {
          startedProcess.getProcess().destroy();
        } finally {
          closeConnection();
        }
      }

      @Override
      public void shutdown() throws Exception {
        try (final PrintWriter pw =
                 new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),
                     StandardCharsets.UTF_8))) {
          pw.println("SHUTDOWN");
        } finally {
          closeConnection();
        }
      }

      @Override
      public int waitFor() throws Exception {
        try {
          return startedProcess.getProcess().waitFor();
        } finally {
          closeConnection();
        }
      }

    };
  }

  private Socket connectToManagementApi(final long timeout, final TimeUnit timeUnit)
      throws Exception {
    final long actualTimeout = System.currentTimeMillis() + timeUnit.toMillis(timeout);

    final InetAddress localhost = InetAddress.getLocalHost();
    while (System.currentTimeMillis() < actualTimeout) {
      try {
        final Socket socket = new Socket();
        socket.connect(new InetSocketAddress(localhost, 12345), 500);
        return socket;
      } catch (final Exception ex) {
        // Nevermind, wait and try again
        Thread.sleep(100L);
      }
    }

    throw new TimeoutException("Cannot connect to TestApp's management interface");
  }

}
