/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import static com.sap.jma.testapi.Matchers.ThrowableMatchers.hasMessageThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sap.jma.logging.Logger;
import com.sap.jma.testapi.TemporarySystemProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class AgentTest {

  @Rule
  public final TemporarySystemProperties temporarySystemProperties =
      TemporarySystemProperties.overlay();

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  private Logger logger = mock(Logger.class);

  @Test
  public void testAgentConfigInvalidEnabled() throws Exception {
    temporarySystemProperties.set(Configuration.Property.ENABLED.getQualifiedName()).to("ja");

    Agent.initAgent(null, logger);

    verify(logger).error(argThat(is("HeapDumpAgent cannot start")),
        argThat(allOf(isA(IllegalArgumentException.class),
            hasMessageThat(is("The value 'ja' is invalid for the 'jma.enabled' property: "
                + "'ja' is not a valid boolean")))));

    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testAgentConfigInvalidLogLevel() throws Exception {
    temporarySystemProperties.set(Configuration.Property.LOG_LEVEL.getQualifiedName()).to("YOLO");

    Agent.initAgent(null, logger);

    verify(logger).error(argThat(is("HeapDumpAgent cannot start")),
        argThat(allOf(isA(IllegalArgumentException.class),
            hasMessageThat(is("The value 'YOLO' is invalid for the 'jma.log_level' property: "
                + "allowed values are: OFF, ERROR, INFO, WARNING, DEBUG")))));

    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testAgentConfigInvalidMaxFrequency() throws Exception {
    temporarySystemProperties.set(Configuration.Property.MAX_HEAP_DUMP_FREQUENCY.getQualifiedName())
        .to("12h");

    Agent.initAgent(null, logger);

    verify(logger).error(argThat(is("HeapDumpAgent cannot start")),
        argThat(allOf(isA(IllegalArgumentException.class),
            hasMessageThat(is("The value '12h' is invalid for the 'jma.max_frequency' "
                + "property: it must follow the Java pattern '(\\d+)/(\\d*)(ms|s|m|h)'")))));

    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testAgentConfigInvalidCheckInterval() throws Exception {
    temporarySystemProperties.set(Configuration.Property.CHECK_INTERVAL.getQualifiedName()).to("4");

    Agent.initAgent(null, logger);

    verify(logger).error(argThat(is("HeapDumpAgent cannot start")),
        argThat(allOf(isA(IllegalArgumentException.class),
            hasMessageThat(is("The value '4' is invalid for the 'jma.check_interval' property: "
                + "it must follow the Java pattern '(\\d*\\.?\\d*\\d)(ms|s|m|h)'")))));

    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testAgentConfigValidNamePattern() throws Exception {
    temporarySystemProperties.set(Configuration.Property.HEAP_DUMP_NAME.getQualifiedName())
        .to("%host_name%_%env:test-var%_%ts%_%uuid%.hprof");

    Agent.initAgent(null, logger);
  }

  @Test
  public void testAgentConfigInvalidNamePattern() throws Exception {
    temporarySystemProperties.set(Configuration.Property.HEAP_DUMP_NAME.getQualifiedName())
        .to("%hostname%");

    Agent.initAgent(null, logger);

    verify(logger).error(argThat(is("HeapDumpAgent cannot start")),
        argThat(allOf(isA(IllegalArgumentException.class),
            hasMessageThat(is("The value '%hostname%' is invalid for the 'jma.heap_dump_name' "
                + "property: the token '%hostname%' (position 0 to 9) is unknown")))));

    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testAgentConfigRelativeHeapDumpFolderPath() throws Exception {
    temporarySystemProperties.set(Configuration.Property.HEAP_DUMP_FOLDER.getQualifiedName())
        .to("dumps");

    Agent.initAgent(null, logger);
  }

}
