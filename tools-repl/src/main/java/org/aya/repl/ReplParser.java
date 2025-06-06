// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import java.util.Collections;
import java.util.List;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;

/**
 * @param shellLike see {@link CommandArg#shellLike}
 */
public record ReplParser<T>(
  @NotNull CommandManager cmd, @NotNull ReplLexer<T> lexer,
  @NotNull DefaultParser shellLike
) implements Parser {
  public ReplParser(@NotNull CommandManager cmd, @NotNull ReplLexer<T> lexer) {
    this(cmd, lexer, new DefaultParser());
  }

  public record ReplParsedLine<T>(
    @Override int wordCursor,
    @NotNull ImmutableSeq<T> rawTokens,
    @Override @NotNull List<@NotNull String> words,
    @Override @NotNull String word,
    @Override int wordIndex,
    @Override @NotNull String line,
    @Override int cursor
  ) implements CompletingParsedLine {
    @Override public CharSequence escape(CharSequence charSequence, boolean b) {
      return charSequence;
    }

    @Override public int rawWordCursor() { return wordCursor; }
    @Override public int rawWordLength() { return word.length(); }
  }

  @Override public ParsedLine parse(String line, int cursor, ParseContext context) throws SyntaxError {
    if (line.isBlank()) return simplest(line, cursor, 0, ImmutableSeq.empty(), Collections.emptyList());
    // ref: https://github.com/jline/jline3/issues/36
    if ((context == ParseContext.UNSPECIFIED || context == ParseContext.ACCEPT_LINE)
      && line.startsWith(Command.MULTILINE_BEGIN) && !line.endsWith(Command.MULTILINE_END)) {
      throw new EOFError(-1, cursor, "In multiline mode");
    }
    var trim = line.trim();
    if (trim.startsWith(Command.PREFIX)) {
      var shellAlike = cmd.parse(trim.substring(1)).command().view()
        .mapNotNull(CommandManager.CommandGen::argFactory)
        .anyMatch(CommandArg::shellLike);
      // ^ if anything matches
      if (shellAlike) return shellLike.parse(line, cursor, context);
    }
    // Drop whitespaces
    lexer.reset(line, 0);
    var tokens = lexer.allTheWayDown()
      .view()
      .filterNot(lexer::isWhitespace)
      .toSeq();
    var wordOpt = tokens.findFirst(token ->
      lexer.containsOffset(token, cursor));
    // In case we're in a whitespace or at the end
    if (wordOpt.isEmpty()) {
      var tokenOpt = tokens.findFirst(tok -> lexer.startOffset(tok) >= cursor);
      if (tokenOpt.isEmpty())
        return simplest(line, cursor, tokens.size(), tokens, tokens
          .map(tok -> textOf(line, tok)).asJava());
      var token = tokenOpt.get();
      var wordCursor = cursor - lexer.startOffset(token);
      return new ReplParsedLine<>(
        Math.max(wordCursor, 0), tokens,
        tokens.map(tok -> textOf(line, tok)).asJava(),
        textOf(line, token), tokens.size() - 1, line, cursor
      );
    }
    var word = wordOpt.get();
    var wordText = textOf(line, word);
    return new ReplParsedLine<>(
      cursor - lexer.startOffset(word),
      tokens,
      tokens.map(tok -> textOf(line, tok)).asJava(),
      wordText, tokens.indexOf(word), line, cursor
    );
  }

  @NotNull private String textOf(String line, T tok) {
    return lexer.tokenText(line, tok);
  }

  public @NotNull ReplParsedLine<T> simplest(
    String line, int cursor, int wordIndex,
    @NotNull ImmutableSeq<T> rawTokens,
    @NotNull List<@NotNull String> tokens
  ) {
    return new ReplParsedLine<>(0, rawTokens, tokens, "", wordIndex, line, cursor);
  }
}
