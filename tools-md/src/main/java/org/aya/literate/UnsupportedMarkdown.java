// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate;

import org.aya.pretty.doc.Doc;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record UnsupportedMarkdown(
  @Override @NotNull SourcePos sourcePos,
  @NotNull String nodeName
) implements Problem {
  @Override public @NotNull Severity level() {
    return Severity.WARN;
  }

  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.english("Unsupported markdown syntax: " + nodeName + ".");
  }
}
