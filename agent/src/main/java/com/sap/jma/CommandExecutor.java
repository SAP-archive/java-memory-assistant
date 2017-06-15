/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma;

import com.sap.jma.logging.Logger;
import java.util.ArrayList;
import java.util.List;

class CommandExecutor {

  private final Configuration configuration;
  private final Logger logger;

  // VisibleForTesting
  CommandExecutor(final Configuration configuration, final Logger logger) {
    this.configuration = configuration;
    this.logger = logger;
  }

  CommandExecutor(final Configuration configuration) {
    this(configuration, Logger.Factory.get(CommandExecutor.class));
  }

  void executeBeforeHeapDumpCommand(final String heapDumpFileName)
      throws CommandExecutionException {
    executeCommand(configuration.getExecuteBefore(), HeapDumpStage.BEFORE, heapDumpFileName);
  }

  void executeAfterHeapDumpCommand(final String heapDumpFileName)
      throws CommandExecutionException {
    executeCommand(configuration.getExecuteAfter(), HeapDumpStage.AFTER, heapDumpFileName);
  }

  void executeOnShutdownCommand() throws CommandExecutionException {
    executeCommand(configuration.getExecuteOnShutDown(), HeapDumpStage.ON_SHUTDOWN, null);
  }

  private void executeCommand(final String command, final HeapDumpStage stage,
                              final String heapDumpFileName) throws CommandExecutionException {
    if (command == null) {
      return;
    }

    String normalizedCommand = command.trim();
    if ((normalizedCommand.startsWith("'") && normalizedCommand.endsWith("'"))
        || (normalizedCommand.startsWith("\"") && normalizedCommand.endsWith("\""))) {
      normalizedCommand = normalizedCommand.substring(1, normalizedCommand.length() - 1);
    }

    final int exitCode;
    try {
      final List<String> commandTokens = new ArrayList<>();

      final String commandInterpreter = configuration.getCommandInterpreter();
      if (commandInterpreter != null && !commandInterpreter.isEmpty()) {
        commandTokens.add(commandInterpreter);
      }

      commandTokens.add(normalizedCommand);

      if (stage != HeapDumpStage.ON_SHUTDOWN) {
        commandTokens.add(heapDumpFileName);
      }

      exitCode = startProcess(commandTokens.toArray(new String[]{})).waitFor();
    } catch (Exception ex) {
      final String message;
      switch (stage) {
        case ON_SHUTDOWN:
          message = String.format("Execution of '%s' on shutdown failed", normalizedCommand);
          break;
        default:
          message = String.format("Execution of '%s' %s heap dump failed", normalizedCommand,
              stage.name().toLowerCase());
      }

      throw new CommandExecutionException(message, ex);
    }

    if (exitCode == 0) {
      final String message;
      switch (stage) {
        case ON_SHUTDOWN:
          message = String.format("Execution of '%s' on shutdown succeeded with exit code 0",
              normalizedCommand);
          break;
        default:
          message = String.format("Execution of '%s' %s heap dump '%s' succeeded with exit code 0",
              normalizedCommand, stage.name().toLowerCase(), heapDumpFileName);
      }

      logger.debug(message);
    } else {
      final String message;
      switch (stage) {
        case ON_SHUTDOWN:
          message = String.format("Execution of '%s' on shutdown failed: exit code %d",
              normalizedCommand, exitCode);
          break;
        default:
          message = String.format("Execution of '%s' %s heap dump failed: exit code %d",
              normalizedCommand, stage.name().toLowerCase(), exitCode);
      }

      throw new CommandExecutionException(message);
    }
  }

  // VisibleForTesting
  Process startProcess(final String... commandAndArguments) throws Exception {
    return new ProcessBuilder().inheritIO().command(commandAndArguments).start();
  }

  private enum HeapDumpStage {
    BEFORE, AFTER, ON_SHUTDOWN
  }

  static class CommandExecutionException extends Exception {

    CommandExecutionException(final String message, final Throwable cause) {
      super(message, cause);
    }

    CommandExecutionException(final String message) {
      super(message);
    }

  }

}
