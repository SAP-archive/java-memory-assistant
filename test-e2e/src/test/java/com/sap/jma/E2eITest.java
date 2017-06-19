/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import static com.sap.jma.testapi.process.ProcessCondition.Factory.fileCreatedIn;
import static com.sap.jma.testapi.process.ProcessCondition.Factory.heapDumpCreatedIn;
import static com.sap.jma.testapi.process.ProcessCondition.Factory.timeElapses;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.sap.jma.Configuration.Property;
import com.sap.jma.testapi.process.Process;
import com.sap.jma.testapi.process.ProcessBuilder;
import com.sap.jma.testapi.process.ProcessCondition;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;

public class E2eITest {

  /*
   * Put TempFolder before Timeout rule to ensure that temp folders are deleted
   * in case of timeout.
   */
  @Rule
  public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule
  public final Timeout globalTimeout = new Timeout(30, TimeUnit.SECONDS);

  private final List<Process> processes = new LinkedList<>();

  private File heapDumpFolder;
  private String javaPath;
  private String jvm;
  private File jarsDir;
  private File dumpsDir;
  private String version;

  private static ProcessCondition hookFileCreatedIn(final File heapDumpFolder, final long timeout,
                                                    final TimeUnit timeUnit) {
    return fileCreatedIn("^hook_invoked$", heapDumpFolder, timeout, timeUnit);
  }

  @Before
  public void setup() throws Exception {
    javaPath = System.getProperty("javaExec", JavaEnvUtils.getJreExecutable("java"));

    heapDumpFolder = tempFolder.newFolder("heap_dumps");

    jvm = System.getProperty("jvm", "unknown-jvm");
    jarsDir = new File(System.getProperty("jarsDir", "build/libs")).getAbsoluteFile();
    dumpsDir = new File(System.getProperty("dumpsDir", "build/oom")).getAbsoluteFile();
    version = System.getProperty("version", "0.0.1-SNAPSHOT");

    if (dumpsDir.exists()) {
      if (!dumpsDir.isDirectory()) {
        throw new IllegalStateException("Dumps folder not a directory: " + dumpsDir);
      }
    } else if (!dumpsDir.mkdirs()) {
      throw new IllegalStateException("Cannot create dumps folder: " + dumpsDir);
    }
  }

  @After
  public void tearDown() {
    final List<Throwable> throwables = new LinkedList<>();
    for (final Process process : processes) {
      try {
        process.destroy();
      } catch (final Exception ex) {
        throwables.add(ex);
      }
    }

    if (!throwables.isEmpty()) {
      final StringBuilder sb = new StringBuilder("Errors while shutting down processes:\n");
      for (final Throwable t : throwables) {
        final StringWriter sw = new StringWriter();
        try (final PrintWriter pw = new PrintWriter(sw)) {
          t.printStackTrace(pw);
        }

        sb.append("---------\n");
        sb.append(sw.toString());
        sb.append('\n');
      }

      throw new AssertionError("Errors while shutting down processes:\n" + sb.toString());
    }

    processes.clear();
  }

  @Test
  public void testAgentHeapAllocation() throws Exception {
    final Process process = createProcessBuilder(heapDumpFolder) //
        .withJvmArgument("-Xms8m") //
        .withJvmArgument("-Xmx8m") //
        .withSystemProperty(Property.LOG_LEVEL.getQualifiedName(), "ERROR") //
        .withSystemProperty(Property.CHECK_INTERVAL.getQualifiedName(), "1s") //
        .withSystemProperty(Property.MAX_HEAP_DUMP_FREQUENCY.getQualifiedName(), "1/3s") //
        .withSystemProperty(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName(), "1%") //
        .withSystemProperty("jma-test.mode", "direct_allocation") //
        .withSystemProperty("jma-test.allocation", "3MB") //
        .withSystemProperty("jma-test.log", "false") //
        .buildAndRunUntil(heapDumpCreatedIn(heapDumpFolder, 1, TimeUnit.MINUTES));

    assertThat(process.getErr(), hasNoErrors());
    assertThat(heapDumpFolder.listFiles(), is(not(Matchers.<File>emptyArray())));
  }

  @Test
  public void testAgentBeforeCommand() throws Exception {
    final Process process = createProcessBuilder(heapDumpFolder) //
        .withJvmArgument("-Xms8m") //
        .withJvmArgument("-Xmx8m") //
        .withSystemProperty(Property.LOG_LEVEL.getQualifiedName(), "ERROR") //
        .withSystemProperty(Property.CHECK_INTERVAL.getQualifiedName(), "1s") //
        .withSystemProperty(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName(), "5%") //
        .withSystemProperty(Property.EXECUTE_BEFORE_HEAP_DUMP.getQualifiedName(),
            getHookScriptName()) //
        .withSystemProperty("jma-test.mode", "direct_allocation") //
        .withSystemProperty("jma-test.allocation", "3MB") //
        .withSystemProperty("jma-test.log", "false") //
        .withEnvironmentProperty("TARGET_FILE_FOLDER", heapDumpFolder.getAbsolutePath()) //
        .buildAndRunUntil(hookFileCreatedIn(heapDumpFolder, 1, TimeUnit.MINUTES));

    assertThat(process.getErr(), hasNoErrors());
  }

  @Test
  public void testAgentAfterCommand() throws Exception {
    final Process process = createProcessBuilder(heapDumpFolder) //
        .withJvmArgument("-Xms8m") //
        .withJvmArgument("-Xmx8m") //
        .withSystemProperty(Property.LOG_LEVEL.getQualifiedName(), "ERROR") //
        .withSystemProperty(Property.CHECK_INTERVAL.getQualifiedName(), "10ms") //
        .withSystemProperty(Property.MAX_HEAP_DUMP_FREQUENCY.getQualifiedName(), "1/3s") //
        .withSystemProperty(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName(), "1%") //
        .withSystemProperty(Property.EXECUTE_AFTER_HEAP_DUMP.getQualifiedName(),
            getHookScriptName()) //
        .withSystemProperty("jma-test.mode", "direct_allocation") //
        .withSystemProperty("jma-test.allocation", "3MB") //
        .withSystemProperty("jma-test.log", "false") //
        .withEnvironmentProperty("TARGET_FILE_FOLDER", heapDumpFolder.getAbsolutePath()) //
        .buildAndRunUntil(hookFileCreatedIn(heapDumpFolder, 1, TimeUnit.MINUTES));

    assertThat(process.getErr(), hasNoErrors());
  }

  @Test
  public void testAgentIncrementOverTimeFrame() throws Exception {
    final Process process = createProcessBuilder(heapDumpFolder) //
        .withJvmArgument("-Xms20m") //
        .withJvmArgument("-Xmx20m") //
        .withSystemProperty(Property.LOG_LEVEL.getQualifiedName(), "ERROR") //
        .withSystemProperty(Property.CHECK_INTERVAL.getQualifiedName(), "10ms") //
        .withSystemProperty(Property.MAX_HEAP_DUMP_FREQUENCY.getQualifiedName(), "1/3s") //
        .withSystemProperty(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName(), "+0.05%/1s") //
        .withSystemProperty("jma-test.mode", "step_wise_increment") //
        .withSystemProperty("jma-test.allocation", "500KB") //
        .withSystemProperty("jma-test.log", "false") //
        .buildAndRunUntil(heapDumpCreatedIn(heapDumpFolder, 1, TimeUnit.MINUTES));

    assertThat(process.getErr(), hasNoErrors());
  }

  @Test
  public void testAgentAbsoluteThresholdStepWiseAllocation() throws Exception {
    final Process process = createProcessBuilder(heapDumpFolder) //
        .withJvmArgument("-Xms20m") //
        .withJvmArgument("-Xmx20m") //
        .withSystemProperty(Property.LOG_LEVEL.getQualifiedName(), "ERROR") //
        .withSystemProperty(Property.CHECK_INTERVAL.getQualifiedName(), "10ms") //
        .withSystemProperty(Property.MAX_HEAP_DUMP_FREQUENCY.getQualifiedName(), "1/3s") //
        .withSystemProperty(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName(), ">2MB") //
        .withSystemProperty("jma-test.mode", "step_wise_increment") //
        .withSystemProperty("jma-test.allocation", "500KB") //
        .withSystemProperty("jma-test.log", "false") //
        .buildAndRunUntil(heapDumpCreatedIn(heapDumpFolder, 1, TimeUnit.MINUTES));

    assertThat(process.getErr(), hasNoErrors());
  }

  @Test
  public void testAgentAbsoluteThresholdFixedAllocationOverThreshold() throws Exception {
    final Process process = createProcessBuilder(heapDumpFolder) //
        .withJvmArgument("-Xms20m") //
        .withJvmArgument("-Xmx20m") //
        .withSystemProperty(Property.LOG_LEVEL.getQualifiedName(), "ERROR") //
        .withSystemProperty(Property.CHECK_INTERVAL.getQualifiedName(), "10ms") //
        .withSystemProperty(Property.MAX_HEAP_DUMP_FREQUENCY.getQualifiedName(), "1/3s") //
        .withSystemProperty(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName(), ">5MB") //
        .withSystemProperty("jma-test.mode", "direct_allocation") //
        .withSystemProperty("jma-test.allocation", "5MB") //
        .withSystemProperty("jma-test.log", "false") //
        .buildAndRunUntil(timeElapses(20, TimeUnit.SECONDS));

    assertThat(heapDumpFolder.listFiles(), not(Matchers.<File>emptyArray()));

    assertThat(process.getErr(), hasNoErrors());
  }

  @Test
  public void testAgentAbsoluteThresholdFixedAllocationUnderThreshold() throws Exception {
    final Process process = createProcessBuilder(heapDumpFolder) //
        .withJvmArgument("-Xms20m") //
        .withJvmArgument("-Xmx20m") //
        .withSystemProperty(Property.LOG_LEVEL.getQualifiedName(), "ERROR") //
        .withSystemProperty(Property.CHECK_INTERVAL.getQualifiedName(), "10ms") //
        .withSystemProperty(Property.MAX_HEAP_DUMP_FREQUENCY.getQualifiedName(), "1/3s") //
        /*
         * This one is tricky: at startup, the JVM allocates more memory which then goes away
         * with the first garbage collection, so we give an outrageously high threshold
         */
        .withSystemProperty(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName(), ">50MB") //
        .withSystemProperty("jma-test.mode", "direct_allocation") //
        .withSystemProperty("jma-test.allocation", "500KB") //
        .withSystemProperty("jma-test.log", "false") //
        .buildAndRunUntil(timeElapses(20, TimeUnit.SECONDS));

    assertThat(heapDumpFolder.listFiles(), Matchers.<File>emptyArray());

    assertThat(process.getErr(), hasNoErrors());
  }

  @Test
  public void testAgentNamePattern() throws Exception {
    final Process process = createProcessBuilder(heapDumpFolder) //
        .withEnvironmentProperty("test_var", "myHeapDump") //
        .withJvmArgument("-Xms20m") //
        .withJvmArgument("-Xmx20m") //
        .withSystemProperty(Property.HEAP_DUMP_NAME.getQualifiedName(),
            "%env:test_var%_%ts%.hprof") //
        .withSystemProperty(Property.LOG_LEVEL.getQualifiedName(), "ERROR") //
        .withSystemProperty(Property.CHECK_INTERVAL.getQualifiedName(), "10ms") //
        .withSystemProperty(Property.MAX_HEAP_DUMP_FREQUENCY.getQualifiedName(), "1/3s") //
        .withSystemProperty(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName(), "+0.05%/1s") //
        .withSystemProperty("jma-test.mode", "step_wise_increment") //
        .withSystemProperty("jma-test.allocation", "500KB") //
        .withSystemProperty("jma-test.log", "false") //
        .buildAndRunUntil(
            fileCreatedIn("myHeapDump_\\d+\\.hprof", heapDumpFolder, 1, TimeUnit.MINUTES));

    assertThat(process.getErr(), hasNoErrors());
  }

  @Test
  public void testAgentOnShutDownCommand() throws Exception {
    final Process process = createProcessBuilder(heapDumpFolder) //
        .withJvmArgument("-Xms8m") //
        .withJvmArgument("-Xmx8m") //
        .withSystemProperty(Property.LOG_LEVEL.getQualifiedName(), "ERROR") //
        .withSystemProperty(Property.CHECK_INTERVAL.getQualifiedName(), "10ms") //
        .withSystemProperty(Property.MAX_HEAP_DUMP_FREQUENCY.getQualifiedName(), "1/3s") //
        .withSystemProperty(Property.HEAP_MEMORY_USAGE_THRESHOLD.getQualifiedName(), "+0.05%/1s") //
        .withSystemProperty(Property.EXECUTE_ON_SHUTDOWN.getQualifiedName(), getHookScriptName()) //
        .withSystemProperty("jma-test.mode", "direct_allocation") //
        .withSystemProperty("jma-test.allocation", "3MB") //
        .withSystemProperty("jma-test.log", "false") //
        .withEnvironmentProperty("TARGET_FILE_FOLDER", heapDumpFolder.getAbsolutePath()) //
        .build();

    process.shutdown();
    process.waitFor();

    assertThat(process.getErr(), hasNoErrors());
    assertThat(heapDumpFolder.list(), hasItemInArray("hook_invoked"));
  }

  private String getHookScriptName() {
    final String scriptName = "hook."
        + (System.getProperty("os.name").toLowerCase().startsWith("win") ? "bat" : "sh");
    return "'src" + File.separatorChar + "test" + //
        File.separatorChar + "resources" + //
        File.separatorChar + "scripts" + //
        File.separatorChar + scriptName + "'";
  }

  private ProcessBuilder createProcessBuilder(final File heapDumpFolder) {
    return new ProcessBuilder(jvm, javaPath, jarsDir, version, dumpsDir) {
      @Override
      public Process build() throws Exception {
        final Process process = super.build();
        processes.add(process);
        return process;
      }
    }.withSystemProperty(Configuration.Property.HEAP_DUMP_FOLDER.getQualifiedName(),
        heapDumpFolder.toString());
  }

  private static Matcher<String> hasNoErrors() {
    return not(com.sap.jma.testapi.Matchers.hasUnexpectedErrors(
        containsString("Class JavaLaunchHelper is implemented in both")));
  }

}
