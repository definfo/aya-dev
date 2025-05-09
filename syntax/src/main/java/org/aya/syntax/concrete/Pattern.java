// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.generic.AyaDocile;
import org.aya.generic.stmt.Shaped;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.ConcretePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.ForLSP;
import org.aya.util.PrettierOptions;
import org.aya.util.position.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Patterns in the concrete syntax.
 *
 * @author kiva, ice1000, HoshinoTented
 */
public sealed interface Pattern extends AyaDocile {
  void forEach(@NotNull PosedConsumer<@NotNull Pattern> f);
  interface Salt { }

  /// Whether a [Pattern] can be a [Pattern.Bind], this is used by [Expr.ClauseLam] in desugarer.
  ///
  /// @see Pattern.Bind
  /// @see Pattern.CalmFace
  interface BindLike {
    /// Returns the [LocalVar] this [Pattern] introduced, with [SourcePos] {@param pos} if it doesn't have one.
    @NotNull LocalVar toLocalVar(@NotNull SourcePos pos);
  }

  @NotNull Pattern descent(@NotNull PosedUnaryOperator<@NotNull Pattern> f);

  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new ConcretePrettier(options).pattern(this, true, BasePrettier.Outer.Free);
  }

  record Tuple(@NotNull WithPos<Pattern> l, @NotNull WithPos<Pattern> r) implements Pattern {
    public @NotNull Tuple update(@NotNull WithPos<Pattern> l, @NotNull WithPos<Pattern> r) {
      return l == this.l && r == this.r ? this : new Tuple(l, r);
    }

    @Override public @NotNull Tuple descent(@NotNull PosedUnaryOperator<@NotNull Pattern> f) {
      return update(l.descent(f), r.descent(f));
    }
    @Override public void forEach(@NotNull PosedConsumer<@NotNull Pattern> f) {
      f.accept(l);
      f.accept(r);
    }
  }

  record Number(int number) implements Pattern {
    @Override public void forEach(@NotNull PosedConsumer<@NotNull Pattern> f) { }
    @Override public @NotNull Number descent(@NotNull PosedUnaryOperator<@NotNull Pattern> f) { return this; }
  }

  enum Absurd implements Pattern {
    INSTANCE;

    @Override public void forEach(@NotNull PosedConsumer<@NotNull Pattern> f) { }
    @Override public @NotNull Pattern descent(@NotNull PosedUnaryOperator<@NotNull Pattern> f) { return this; }
  }

  enum CalmFace implements Pattern, BindLike {
    INSTANCE;

    @Override public void forEach(@NotNull PosedConsumer<@NotNull Pattern> f) { }
    @Override public @NotNull Pattern descent(@NotNull PosedUnaryOperator<@NotNull Pattern> f) { return this; }
    @Override public @NotNull LocalVar toLocalVar(@NotNull SourcePos pos) { return LocalVar.generate(pos); }
  }

  /**
   * @param type used in the LSP server
   */
  record Bind(
    @NotNull LocalVar bind,
    @ForLSP @NotNull MutableValue<@Nullable Term> type
  ) implements Pattern, BindLike {
    public Bind(@NotNull LocalVar bind) { this(bind, MutableValue.create()); }
    @Override public void forEach(@NotNull PosedConsumer<@NotNull Pattern> f) { }
    @Override public @NotNull Bind descent(@NotNull PosedUnaryOperator<@NotNull Pattern> f) { return this; }
    @Override public @NotNull LocalVar toLocalVar(@NotNull SourcePos pos) { return bind; }
  }

  record Con(
    @NotNull WithPos<@NotNull ConDefLike> resolved,
    @NotNull ImmutableSeq<Arg<WithPos<Pattern>>> params
  ) implements Pattern {
    public Con(@NotNull SourcePos pos, @NotNull ConDefLike maybe) {
      this(new WithPos<>(pos, maybe), ImmutableSeq.empty());
    }

    public @NotNull Con update(@NotNull ImmutableSeq<Arg<WithPos<Pattern>>> params) {
      return params.sameElements(params(), true) ? this : new Con(resolved, params);
    }

    @Override public void forEach(@NotNull PosedConsumer<@NotNull Pattern> f) {
      params.forEach(x -> f.accept(x.term()));
    }
    @Override public @NotNull Con descent(@NotNull PosedUnaryOperator<@NotNull Pattern> f) {
      return update(params.map(a -> a.descent(x -> x.descent(f))));
    }
  }

  record BinOpSeq(@NotNull ImmutableSeq<Arg<WithPos<Pattern>>> seq) implements Pattern, Salt {
    public @NotNull BinOpSeq update(@NotNull ImmutableSeq<Arg<WithPos<Pattern>>> seq) {
      return seq.sameElements(seq(), true) ? this : new BinOpSeq(seq);
    }

    @Override public @NotNull BinOpSeq descent(@NotNull PosedUnaryOperator<@NotNull Pattern> f) {
      return update(seq.map(a -> a.descent(x -> x.descent(f))));
    }
    @Override public void forEach(@NotNull PosedConsumer<@NotNull Pattern> f) { seq.forEach(x -> f.accept(x.term())); }
  }

  /**
   * Represent a {@code (Pattern) as bind} pattern
   */
  record As(
    @NotNull WithPos<Pattern> pattern,
    @NotNull LocalVar as,
    @ForLSP @NotNull MutableValue<@Nullable Term> type
  ) implements Pattern {
    public static Arg<Pattern> wrap(@NotNull Arg<WithPos<Pattern>> pattern, @NotNull LocalVar var) {
      return new Arg<>(new As(pattern.term(), var, MutableValue.create()), pattern.explicit());
    }

    public @NotNull As update(@NotNull WithPos<Pattern> pattern) {
      return pattern == pattern() ? this : new As(pattern, as, type);
    }

    @Override public @NotNull As descent(@NotNull PosedUnaryOperator<@NotNull Pattern> f) {
      return update(pattern.descent(f));
    }
    @Override public void forEach(@NotNull PosedConsumer<@NotNull Pattern> f) { f.accept(pattern); }
  }

  /**
   * @param qualifiedID a qualified QualifiedID ({@code isUnqualified == false})
   */
  record QualifiedRef(
    @NotNull QualifiedID qualifiedID,
    @Nullable WithPos<Expr> userType,
    @ForLSP @NotNull MutableValue<@Nullable Term> type
  ) implements Pattern, Salt {
    public QualifiedRef(@NotNull QualifiedID qualifiedID) {
      this(qualifiedID, null, MutableValue.create());
    }

    @Override public @NotNull QualifiedRef descent(@NotNull PosedUnaryOperator<@NotNull Pattern> f) { return this; }
    @Override public void forEach(@NotNull PosedConsumer<@NotNull Pattern> f) { }
  }

  /** Sugared List Pattern */
  record List(@NotNull ImmutableSeq<WithPos<Pattern>> elements) implements Pattern {
    public @NotNull List update(@NotNull ImmutableSeq<WithPos<Pattern>> elements) {
      return elements.sameElements(elements(), true) ? this : new List(elements);
    }

    @Override public void forEach(@NotNull PosedConsumer<@NotNull Pattern> f) { }
    @Override public @NotNull List descent(@NotNull PosedUnaryOperator<@NotNull Pattern> f) {
      return update(elements.map(x -> x.descent(f)));
    }
  }
  record FakeShapedList(
    @NotNull SourcePos sourcePos,
    @Override @NotNull ImmutableSeq<WithPos<Pattern>> repr,
    @NotNull ConDefLike nil,
    @NotNull ConDefLike cons,
    @Override @NotNull DataCall type
  ) implements Shaped.List<WithPos<Pattern>> {
    @Override public @NotNull WithPos<Pattern> makeNil() {
      return new WithPos<>(sourcePos, new Con(sourcePos, nil));
    }
    @Override public @NotNull WithPos<Pattern> makeCons(WithPos<Pattern> x, WithPos<Pattern> xs) {
      return new WithPos<>(sourcePos, new Con(new WithPos<>(sourcePos, cons),
        ImmutableSeq.of(new Arg<>(x, true), new Arg<>(xs, true))));
    }
    @Override public @NotNull WithPos<Pattern> destruct(@NotNull ImmutableSeq<WithPos<Pattern>> repr) {
      return new FakeShapedList(sourcePos, repr, nil, cons, type).constructorForm();
    }
  }

  /**
   * @author kiva, ice1000
   */
  final class Clause implements SourceNode {
    public final @NotNull SourcePos sourcePos;
    public final @NotNull ImmutableSeq<Arg<WithPos<Pattern>>> patterns;
    public final @NotNull Option<WithPos<Expr>> expr;
    public boolean hasError = false;

    public Clause(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<Arg<WithPos<Pattern>>> patterns, @NotNull Option<WithPos<Expr>> expr) {
      this.sourcePos = sourcePos;
      this.patterns = patterns;
      this.expr = expr;
    }

    public @NotNull Clause update(@NotNull ImmutableSeq<Arg<WithPos<Pattern>>> pats, @NotNull Option<WithPos<Expr>> body) {
      return body.sameElements(expr, true) && pats.sameElements(patterns, true) ? this
        : new Clause(sourcePos, pats, body);
    }

    public @NotNull Clause descent(@NotNull PosedUnaryOperator<@NotNull Expr> f, @NotNull PosedUnaryOperator<@NotNull Pattern> g) {
      return update(patterns.map(p -> p.descent(x -> x.descent(g))), expr.map(x -> x.descent(f)));
    }
    public void forEach(@NotNull PosedConsumer<@NotNull Expr> f, @NotNull PosedConsumer<@NotNull Pattern> g) {
      patterns.forEach(a -> g.accept(a.term()));
      expr.forEach(f::accept);
    }

    @Override public @NotNull SourcePos sourcePos() { return sourcePos; }
  }
}
