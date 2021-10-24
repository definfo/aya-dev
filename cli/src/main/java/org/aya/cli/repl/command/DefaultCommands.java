// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.util.NormalizeMode;
import org.aya.cli.repl.Repl;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class DefaultCommands {
  private DefaultCommands() {
  }

  public static ImmutableSeq<Command> defaultCommands() {
    return ImmutableSeq.of(
      QUIT,
      CHANGE_PROMPT,
      CHANGE_NORM_MODE,
      HelpCommand.INSTANCE,
      SHOW_TYPE,
      CHANGE_PP_WIDTH,
      TOGGLE_UNICODE
    );
  }

  public static final @NotNull Command CHANGE_PROMPT = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("prompt");
    }

    @Override public @NotNull String description() {
      return "Change the REPL prompt text";
    }

    @Override public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      repl.replConfig.prompt = argument;
      return Result.ok("Changed prompt to `" + argument + "`", true);
    }
  };

  public static final @NotNull Command SHOW_TYPE = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("t", "type");
    }

    @Override public @NotNull String description() {
      return "Show the type of the given expression";
    }

    @Override public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      var type = repl.replCompiler.compileExprAndGetType(argument, repl.replConfig.normalizeMode);
      return type != null ? Result.ok(repl.render(type), true)
        : Result.err("Failed to get expression type", true);
    }
  };

  public static final @NotNull Command CHANGE_PP_WIDTH = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("print-width");
    }

    @Override public @NotNull String description() {
      return "Set printed output width";
    }

    @Override
    public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      var prettyPrintWidth = Integer.parseInt(argument.trim());
      repl.prettyPrintWidth = prettyPrintWidth;
      return Result.ok("Printed output width set to " + prettyPrintWidth, true);
    }
  };

  public static final @NotNull Command QUIT = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("quit", "exit", "q");
    }

    @Override public @NotNull String description() {
      return "Quit the REPL";
    }

    @Override
    public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      return Result.ok("See you space cow woof woof :3", false);
    }
  };

  public static final @NotNull Command CHANGE_NORM_MODE = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("normalize");
    }

    @Override public @NotNull String description() {
      return "Set the normalize mode (candidates: " + Arrays.toString(NormalizeMode.values()) + ")";
    }

    @Override
    public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      var normalizeMode = NormalizeMode.valueOf(argument.trim());
      repl.replConfig.normalizeMode = normalizeMode;
      return Result.ok("Normalize mode set to " + normalizeMode, true);
    }
  };

  public static final @NotNull Command TOGGLE_UNICODE = new Command() {
    @Override public @NotNull ImmutableSeq<String> names() {
      return ImmutableSeq.of("unicode");
    }

    @Override public @NotNull String description() {
      return "Enable or disable unicode in REPL output";
    }

    @Override public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
      var trim = argument.trim();
      boolean enableUnicode = trim.isEmpty() ? !repl.replConfig.enableUnicode
        : Boolean.parseBoolean(trim);
      repl.replConfig.enableUnicode = enableUnicode;
      return Result.ok("Unicode " + (enableUnicode ? "enabled" : "disabled"), true);
    }
  };
}
