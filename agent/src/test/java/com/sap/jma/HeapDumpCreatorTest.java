/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.sap.jma.configuration.Configuration;
import com.sap.jma.logging.Logger;
import com.sap.jma.testapi.TemporaryDefaultTimeZone;
import java.io.File;
import java.lang.management.PlatformManagedObject;
import java.util.Date;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Matchers;
import org.mockito.Mockito;

public class HeapDumpCreatorTest {

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  @Rule
  public final TemporaryDefaultTimeZone tempDefaultTimeZone = TemporaryDefaultTimeZone.toBe("UTC");

  @Rule
  public final TemporaryFolder tempFolder = new TemporaryFolder();

  private final Logger logger = mock(Logger.class);

  private final Configuration configuration = mock(Configuration.class);

  private final HotSpotDiagnosticMxBean heapDumpBean = mock(HotSpotDiagnosticMxBean.class);

  private final CommandExecutor commandExecutor = mock(CommandExecutor.class);

  private final Date now = new Date();

  private HeapDumpCreator subject;

  @BeforeClass
  public static void setupClass() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @Before
  public void setup() throws Exception {
    doReturn(Configuration.DEFAULT_NAME_PATTERN).when(configuration).getHeapDumpName();
    doReturn("sh").when(configuration).getCommandInterpreter();
    subject =
        spy(new HeapDumpCreator(configuration, "myHost", heapDumpBean, commandExecutor, logger));
  }

  @After
  public void tearDown() {
    verifyNoMoreInteractions(commandExecutor);
    verifyNoMoreInteractions(heapDumpBean);
  }

  @Test
  public void testHeapDumpDefaultFolder() throws Exception {
    testHeapDump(null);

    verify(commandExecutor).executeBeforeHeapDumpCommand(tempFolder.getRoot().toString()
        + File.separatorChar + "heapdump_myHost_19700115065607.hprof");
    verify(heapDumpBean).dumpHeap(tempFolder.getRoot().toString() + File.separatorChar
        + "heapdump_myHost_19700115065607.hprof", true);
    verify(commandExecutor).executeAfterHeapDumpCommand(tempFolder.getRoot().toString()
        + File.separatorChar + "heapdump_myHost_19700115065607.hprof");
  }

  @Test
  public void testHeapDumpCustomFolder() throws Exception {
    testHeapDump("test");

    verify(commandExecutor).executeBeforeHeapDumpCommand(tempFolder.getRoot().toString()
        + File.separatorChar + "test" + File.separatorChar
        + "heapdump_myHost_19700115065607.hprof");
    verify(heapDumpBean).dumpHeap(tempFolder.getRoot().toString() + File.separatorChar
        + "test" + File.separatorChar + "heapdump_myHost_19700115065607.hprof", true);
    verify(commandExecutor).executeAfterHeapDumpCommand(tempFolder.getRoot().toString()
        + File.separatorChar + "test" + File.separatorChar
        + "heapdump_myHost_19700115065607.hprof");
  }

  @Test
  public void testExecuteBeforeThrows() throws Exception {
    final CommandExecutor.CommandExecutionException toBeThrown =
        new CommandExecutor.CommandExecutionException("test", null);
    doThrow(toBeThrown).when(commandExecutor).executeBeforeHeapDumpCommand(anyString());

    testHeapDump("test", false);

    verify(commandExecutor).executeBeforeHeapDumpCommand(tempFolder.getRoot().toString()
        + File.separatorChar + "test" + File.separatorChar
        + "heapdump_myHost_19700115065607.hprof");
    verify(logger).error("Execution of command before heap dump '%s' failed",
        tempFolder.getRoot().toString() + File.separatorChar + "test"
        + File.separatorChar + "heapdump_myHost_19700115065607.hprof", toBeThrown);
  }

  @Test
  public void testExecuteAfterThrows() throws Exception {
    final CommandExecutor.CommandExecutionException toBeThrown =
        new CommandExecutor.CommandExecutionException("test", null);
    doThrow(toBeThrown).when(commandExecutor).executeAfterHeapDumpCommand(anyString());

    doReturn("echo").when(configuration).getExecuteAfter();

    testHeapDump("test");

    verify(commandExecutor).executeBeforeHeapDumpCommand(tempFolder.getRoot().toString()
        + File.separatorChar + "test" + File.separatorChar
        + "heapdump_myHost_19700115065607.hprof");
    verify(heapDumpBean).dumpHeap(tempFolder.getRoot().toString() + File.separatorChar
        + "test" + File.separatorChar + "heapdump_myHost_19700115065607.hprof", true);
    verify(commandExecutor).executeAfterHeapDumpCommand(tempFolder.getRoot().toString()
        + File.separatorChar + "test" + File.separatorChar
        + "heapdump_myHost_19700115065607.hprof");
    verify(logger).error("Execution of command after heap dump '%s' failed",
        tempFolder.getRoot().toString() + File.separatorChar + "test" + File.separatorChar
        + "heapdump_myHost_19700115065607.hprof", toBeThrown);
  }

  private void testHeapDump(final String folderName) throws Exception {
    testHeapDump(folderName, true);
  }

  private void testHeapDump(final String folderName, final boolean assumeHeapDumpSucceeded)
      throws Exception {
    final File directory;
    if (folderName == null) {
      directory = tempFolder.getRoot();
    } else {
      directory = tempFolder.newFolder(folderName);
    }

    doReturn(directory).when(configuration).getHeapDumpFolder();

    now.setTime(1234567890);

    subject.createHeapDump(now);

    if (assumeHeapDumpSucceeded) {
      verify(logger).info("Heap dump '%s' created", directory + File.separator
          + "heapdump_myHost_19700115065607.hprof");
      verify(heapDumpBean).dumpHeap(Mockito.eq(directory + File.separator
          + "heapdump_myHost_19700115065607.hprof"), Matchers.eq(true));
    } else {
      verifyZeroInteractions(heapDumpBean);
    }
  }

  private interface HotSpotDiagnosticMxBean extends PlatformManagedObject {

    void dumpHeap(String outputFile, boolean live);

  }

}