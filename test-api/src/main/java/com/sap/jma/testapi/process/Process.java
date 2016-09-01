/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

package com.sap.jma.testapi.process;

public interface Process {

  String getOut();

  String getErr();

  void destroy() throws Exception;

  void shutdown() throws Exception;

  int waitFor() throws Exception;

}
