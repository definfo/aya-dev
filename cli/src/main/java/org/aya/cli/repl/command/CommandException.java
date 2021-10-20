// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

public class CommandException extends RuntimeException {
  public CommandException(String message) {
    super(message);
  }
}
