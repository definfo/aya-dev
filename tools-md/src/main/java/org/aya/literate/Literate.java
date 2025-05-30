// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.pretty.doc.*;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author hoshino
 * @see LiterateConsumer
 */
public interface Literate extends Docile {
  default @Nullable FrontMatter findFrontMatter() { return null; }

  record Raw(@NotNull Doc toDoc) implements Literate { }
  @NotNull Raw EOL = new Raw(Doc.line());

  record List(@NotNull ImmutableSeq<Literate> items, boolean ordered) implements Literate {
    @Override public @NotNull Doc toDoc() {
      return Doc.list(ordered, items.map(Literate::toDoc));
    }
  }

  record HyperLink(
    @NotNull String href, @Nullable String hover,
    @NotNull ImmutableSeq<Literate> children
  ) implements Literate {
    @Override public @NotNull Doc toDoc() {
      var child = Doc.cat(this.children().map(Literate::toDoc));
      return Doc.hyperLink(child, Link.page(href), hover);
    }
  }

  record Image(@NotNull String src, @NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public @NotNull Doc toDoc() {
      var child = Doc.cat(this.children().map(Literate::toDoc));
      return Doc.image(child, Link.page(src));
    }
  }

  record Math(boolean inline, @NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public @NotNull Doc toDoc() {
      var child = Doc.cat(this.children().map(Literate::toDoc));
      return inline ? Doc.math(child) : Doc.mathBlock(child);
    }
  }

  record Many(@Nullable Style style, @NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public @NotNull Doc toDoc() {
      var child = Doc.cat(this.children().map(Literate::toDoc));
      return style == null ? child : Doc.styled(style, child);
    }
    @Override public @Nullable FrontMatter findFrontMatter() {
      return children.view().filterIsInstance(FrontMatter.class).getFirstOrNull();
    }
  }

  record FrontMatter(@NotNull MutableList<Literate> children) implements Literate {
    @Override public @NotNull Doc toDoc() {
      return Doc.cat(this.children().map(Literate::toDoc));
    }
    @Override public @NotNull FrontMatter findFrontMatter() { return this; }
  }

  class InlineCode implements Literate {
    /**
     * The content of this inline code
     */
    public final @NotNull String code;
    /**
     * The source pos of this inline code
     */
    public final @NotNull SourcePos sourcePos;

    public InlineCode(@NotNull String code, @NotNull SourcePos sourcePos) {
      this.code = code;
      this.sourcePos = sourcePos;
    }

    @Override public @NotNull Doc toDoc() { return Doc.code(code); }
  }

  class CodeBlock implements Literate {
    public final @NotNull String language;
    public final @NotNull String code;

    /**
     * The source pos of this code block, without '```\n' and '\n```'<br/>
     * It is null if the code block is empty, because we are unable to construct an empty {@link SourcePos}
     */
    public final @Nullable SourcePos sourcePos;
    public @Nullable Doc highlighted = null;

    /**
     * @param language  used as the `class` attribute of the code block
     * @param sourcePos without '```\n' and '\n```'
     */
    public CodeBlock(@NotNull String language, @NotNull String code, @Nullable SourcePos sourcePos) {
      this.language = language;
      this.code = code;
      this.sourcePos = sourcePos;
    }

    @Override public @NotNull Doc toDoc() {
      var content = highlighted != null ? highlighted : Doc.plain(code);
      return Doc.codeBlock(Language.of(language), content);
    }
  }

  record Unsupported(@NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public @NotNull Doc toDoc() {
      return Doc.vcat(children.map(Literate::toDoc));
    }
  }
}
