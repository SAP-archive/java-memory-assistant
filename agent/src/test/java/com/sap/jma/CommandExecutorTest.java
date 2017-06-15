/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sap.jma.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CommandExecutorTest {

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  private final Logger logger = mock(Logger.class);

  private final Process process = mock(Process.class);

  private final Configuration configuration = mock(Configuration.class);

  private final CommandExecutor subject = spy(new CommandExecutor(configuration, logger));

  @Before
  public void setup() {
    doReturn("sh").when(configuration).getCommandInterpreter();
  }

  @After
  public void tearDown() {
    verifyNoMoreInteractions(process);
    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testExecuteBeforeNoCommandInterpreter() throws Exception {
    doReturn(null).when(configuration).getCommandInterpreter();

    doReturn(process).when(subject).startProcess(anyString(), anyString());

    doReturn("echo").when(configuration).getExecuteBefore();

    subject.executeBeforeHeapDumpCommand("/test/heapdump_myHost_19700115065607.hprof");

    verify(logger).debug("Execution of 'echo' before heap dump "
        + "'/test/heapdump_myHost_19700115065607.hprof' succeeded with exit code 0");
    verify(process).waitFor();
  }

  @Test
  public void testExecuteBeforeNoCommand() throws Exception {
    subject.executeBeforeHeapDumpCommand("/test/heapdump_myHost_19700115065607.hprof");

    verify(subject, never()).startProcess();
  }

  @Test
  public void testExecuteBeforeSuccess() throws Exception {
    doReturn(0).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString(), anyString());

    doReturn("echo").when(configuration).getExecuteBefore();

    subject.executeBeforeHeapDumpCommand("/test/heapdump_myHost_19700115065607.hprof");

    verify(logger).debug("Execution of 'echo' before heap dump "
        + "'/test/heapdump_myHost_19700115065607.hprof' succeeded with exit code 0");
    verify(process).waitFor();
  }

  @Test
  public void testExecuteBeforeStatusCodePositive() throws Exception {
    doReturn(42).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString(), anyString());

    doReturn("echo").when(configuration).getExecuteBefore();

    expectedException.expect(CommandExecutor.CommandExecutionException.class);
    expectedException.expectMessage(
        is("Execution of 'echo' before heap dump failed: exit code 42"));
    expectedException.expectCause(nullValue(Throwable.class));

    try {
      subject.executeBeforeHeapDumpCommand("/test/heapdump_myHost_19700115065607.hprof");
    } finally {
      verify(subject).startProcess("sh", "echo", "/test/heapdump_myHost_19700115065607.hprof");
      verify(process).waitFor();
    }
  }

  @Test
  public void testExecuteBeforeFails() throws Exception {
    final Exception toBeThrown = new IllegalStateException();
    doThrow(toBeThrown).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString(), anyString());

    doReturn("echo").when(configuration).getExecuteBefore();

    expectedException.expect(CommandExecutor.CommandExecutionException.class);
    expectedException.expectMessage(is("Execution of 'echo' before heap dump failed"));
    expectedException.expectCause(sameInstance(toBeThrown));

    try {
      subject.executeBeforeHeapDumpCommand("/test/heapdump_myHost_19700115065607.hprof");
    } finally {
      verify(process).waitFor();
    }
  }

  @Test
  public void testExecuteBeforeEscapesSingleQuotes() throws Exception {
    doReturn(0).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString(), anyString());

    doReturn("'echo'").when(configuration).getExecuteBefore();

    subject.executeBeforeHeapDumpCommand("heapdump_myHost_19700115065607.hprof");

    verify(subject).startProcess("sh", "echo", "heapdump_myHost_19700115065607.hprof");
    verify(process).waitFor();
    verify(logger).debug("Execution of 'echo' before heap dump "
        + "'heapdump_myHost_19700115065607.hprof' succeeded with exit code 0");
  }

  @Test
  public void testExecuteBeforeEscapesDoubleQuotes() throws Exception {
    doReturn(0).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString(), anyString());

    doReturn("\"echo\"").when(configuration).getExecuteBefore();

    subject.executeBeforeHeapDumpCommand("heapdump_myHost_19700115065607.hprof");

    verify(subject).startProcess("sh", "echo", "heapdump_myHost_19700115065607.hprof");
    verify(process).waitFor();
    verify(logger).debug("Execution of 'echo' before heap dump "
        + "'heapdump_myHost_19700115065607.hprof' succeeded with exit code 0");
  }

  @Test
  public void testExecuteAfterNoCommandInterpreter() throws Exception {
    doReturn(null).when(configuration).getCommandInterpreter();

    doReturn(process).when(subject).startProcess(anyString(), anyString());

    doReturn("echo").when(configuration).getExecuteAfter();

    subject.executeAfterHeapDumpCommand("/test/heapdump_myHost_19700115065607.hprof");

    verify(logger).debug("Execution of 'echo' after heap dump "
        + "'/test/heapdump_myHost_19700115065607.hprof' succeeded with exit code 0");
    verify(process).waitFor();
  }

  @Test
  public void testExecuteAfterNoCommand() throws Exception {
    subject.executeAfterHeapDumpCommand("/test/heapdump_myHost_19700115065607.hprof");

    verify(subject, never()).startProcess();
  }

  @Test
  public void testExecuteAfterSuccess() throws Exception {
    doReturn(0).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString(), anyString());

    doReturn("echo").when(configuration).getExecuteAfter();

    subject.executeAfterHeapDumpCommand("/test/heapdump_myHost_19700115065607.hprof");

    verify(process).waitFor();
    verify(logger).debug("Execution of 'echo' after heap dump "
        + "'/test/heapdump_myHost_19700115065607.hprof' succeeded with exit code 0");
  }

  @Test
  public void testExecuteAfterStatusCodePositive() throws Exception {
    doReturn(42).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString(), anyString());

    doReturn("echo").when(configuration).getExecuteAfter();

    expectedException.expect(CommandExecutor.CommandExecutionException.class);
    expectedException.expectMessage(is("Execution of 'echo' after heap dump failed: exit code 42"));
    expectedException.expectCause(nullValue(Throwable.class));

    try {
      subject.executeAfterHeapDumpCommand("/test/heapdump_myHost_19700115065607.hprof");
    } finally {
      verify(subject).startProcess("sh", "echo", "/test/heapdump_myHost_19700115065607.hprof");
      verify(process).waitFor();
    }
  }

  @Test
  public void testExecuteAfterFails() throws Exception {
    final Exception toBeThrown = new IllegalStateException();
    doThrow(toBeThrown).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString(), anyString());

    doReturn("echo").when(configuration).getExecuteAfter();

    expectedException.expect(CommandExecutor.CommandExecutionException.class);
    expectedException.expectMessage(is("Execution of 'echo' after heap dump failed"));
    expectedException.expectCause(sameInstance(toBeThrown));

    try {
      subject.executeAfterHeapDumpCommand("/test/heapdump_myHost_19700115065607.hprof");
    } finally {
      verify(process).waitFor();
    }
  }

  @Test
  public void testExecuteAfterEscapesSingleQuotes() throws Exception {
    doReturn(0).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString(), anyString());

    doReturn("'echo'").when(configuration).getExecuteAfter();

    subject.executeAfterHeapDumpCommand("heapdump_myHost_19700115065607.hprof");

    verify(subject).startProcess("sh", "echo", "heapdump_myHost_19700115065607.hprof");
    verify(process).waitFor();
    verify(logger).debug("Execution of 'echo' after heap dump "
        + "'heapdump_myHost_19700115065607.hprof' succeeded with exit code 0");
  }

  @Test
  public void testExecuteAfterEscapesDoubleQuotes() throws Exception {
    doReturn(0).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString(), anyString());

    doReturn("\"echo\"").when(configuration).getExecuteAfter();

    subject.executeAfterHeapDumpCommand("heapdump_myHost_19700115065607.hprof");

    verify(subject).startProcess("sh", "echo", "heapdump_myHost_19700115065607.hprof");
    verify(process).waitFor();
    verify(logger).debug("Execution of 'echo' after heap dump "
        + "'heapdump_myHost_19700115065607.hprof' succeeded with exit code 0");
  }

  @Test
  public void testExecuteOnShutdownNoCommandInterpreter() throws Exception {
    doReturn(null).when(configuration).getCommandInterpreter();

    doReturn(process).when(subject).startProcess(anyString());

    doReturn("echo").when(configuration).getExecuteOnShutDown();

    subject.executeOnShutdownCommand();

    verify(logger).debug("Execution of 'echo' on shutdown succeeded with exit code 0");
    verify(process).waitFor();
  }

  @Test
  public void testExecuteOnShutdownNoCommand() throws Exception {
    subject.executeOnShutdownCommand();

    verify(subject, never()).startProcess();
  }

  @Test
  public void testExecuteOnShutdownSuccess() throws Exception {
    doReturn(0).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString());

    doReturn("echo").when(configuration).getExecuteOnShutDown();

    subject.executeOnShutdownCommand();

    verify(process).waitFor();
    verify(logger).debug("Execution of 'echo' on shutdown succeeded with exit code 0");
  }

  @Test
  public void testExecuteOnShutdownStatusCodePositive() throws Exception {
    doReturn(42).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString());

    doReturn("echo").when(configuration).getExecuteOnShutDown();

    expectedException.expect(CommandExecutor.CommandExecutionException.class);
    expectedException.expectMessage(is("Execution of 'echo' on shutdown failed: exit code 42"));
    expectedException.expectCause(nullValue(Throwable.class));

    try {
      subject.executeOnShutdownCommand();
    } finally {
      verify(subject).startProcess("sh", "echo");
      verify(process).waitFor();
    }
  }

  @Test
  public void testExecuteOnShutdownFails() throws Exception {
    final Exception toBeThrown = new IllegalStateException();
    doThrow(toBeThrown).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString());

    doReturn("echo").when(configuration).getExecuteOnShutDown();

    expectedException.expect(CommandExecutor.CommandExecutionException.class);
    expectedException.expectMessage(is("Execution of 'echo' on shutdown failed"));
    expectedException.expectCause(sameInstance(toBeThrown));

    try {
      subject.executeOnShutdownCommand();
    } finally {
      verify(process).waitFor();
    }
  }

  @Test
  public void testExecuteOnShutdownEscapesSingleQuotes() throws Exception {
    doReturn(0).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString());

    doReturn("'echo'").when(configuration).getExecuteOnShutDown();

    subject.executeOnShutdownCommand();

    verify(subject).startProcess("sh", "echo");
    verify(process).waitFor();
    verify(logger).debug("Execution of 'echo' on shutdown succeeded with exit code 0");
  }

  @Test
  public void testExecuteOnShutdownEscapesDoubleQuotes() throws Exception {
    doReturn(0).when(process).waitFor();
    doReturn(process).when(subject).startProcess(anyString(), anyString());

    doReturn("\"echo\"").when(configuration).getExecuteOnShutDown();

    subject.executeOnShutdownCommand();

    verify(subject).startProcess("sh", "echo");
    verify(process).waitFor();
    verify(logger).debug("Execution of 'echo' on shutdown succeeded with exit code 0");
  }

}
