// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.aya.pretty.doc.Doc;
import org.aya.repl.Command.Output;
import org.aya.repl.Command.Result;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public interface ReplUtil {
  static @NotNull Result invokeHelp(CommandManager commandManager, @Nullable HelpItem argument) {
    if (argument != null && !argument.cmd.isEmpty()) {
      return commandManager.cmd.find(c -> c.owner().names().contains(argument.cmd))
        .getOrElse(
          it -> Result.ok(it.owner().help(), true),
          () -> Result.err("No such command: " + argument.cmd, true));
    }
    var commands = Doc.vcat(commandManager.cmd.view()
      .map(command -> Doc.sep(
        Doc.commaList(command.owner().names().map(name -> Doc.plain(Command.PREFIX + name))),
        Doc.symbol("-"),
        Doc.english(command.owner().help())
      )));
    return new Result(Output.stdout(commands), true);
  }

  record HelpItem(@NotNull String cmd) {
  }

  static @NotNull Path resolveFile(@NotNull String arg, Path cwd) {
    var homeAware = arg.replaceFirst("^~", System.getProperty("user.home"));
    var path = Path.of(homeAware);
    return path.isAbsolute() ? path.normalize() : cwd.resolve(homeAware).toAbsolutePath().normalize();
  }

  static @NotNull String red(@NotNull String x) {
    return new AttributedStringBuilder()
      .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
      .append(x)
      .style(AttributedStyle.DEFAULT)
      .toAnsi();
  }

  static @NotNull Consumer<String> jlineDumbTerminalWriter() throws IOException {
    var terminal = TerminalBuilder.builder().jni(true).dumb(true).build();
    return s -> {
      terminal.writer().println(s);
      terminal.flush();
    };
  }
}
