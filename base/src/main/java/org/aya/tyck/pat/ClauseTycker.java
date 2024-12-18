// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.value.primitive.MutableBooleanValue;
import org.aya.generic.Renamer;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.telescope.Signature;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.Jdg;
import org.aya.tyck.TyckState;
import org.aya.tyck.ctx.LocalLet;
import org.aya.tyck.error.PatternProblem;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.error.Panic;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

public record ClauseTycker(@NotNull ExprTycker exprTycker) implements Problematic, Stateful {
  public record TyckResult(
    @NotNull ImmutableSeq<Pat.Preclause<Term>> clauses,
    @NotNull ImmutableSeq<Term.Matching> wellTyped,
    boolean hasLhsError
  ) { }

  /**
   * @param paramSubst substitution for parameter, in the same order as parameter.
   *                   See {@link PatternTycker#paramSubst}
   * @param freePats   a free version of the patterns.
   *                   In most cases you want to use {@code clause.pats} instead
   * @param allBinds   all binders in the patterns
   * @param asSubst    substitution of the {@code as} patterns
   */
  public record LhsResult(
    @NotNull LocalCtx localCtx,
    @NotNull Term type,
    @NotNull ImmutableSeq<LocalVar> allBinds,
    @NotNull ImmutableSeq<Pat> freePats,
    @NotNull ImmutableSeq<Jdg> paramSubst,
    @NotNull LocalLet asSubst,
    @NotNull Pat.Preclause<Expr> clause,
    boolean hasError
  ) {
    @Contract(mutates = "param2")
    public void addLocalLet(@NotNull ImmutableSeq<LocalVar> teleBinds, @NotNull ExprTycker exprTycker) {
      teleBinds.forEachWith(paramSubst, exprTycker.localLet()::put);
      exprTycker.setLocalLet(new LocalLet(exprTycker.localLet(), asSubst.subst()));
    }
  }

  public record Worker(
    @NotNull ClauseTycker parent,
    @NotNull ImmutableSeq<LocalVar> vars,
    @NotNull Signature signature,
    @NotNull ImmutableSeq<Pattern.Clause> clauses,
    @NotNull ImmutableSeq<LocalVar> elims,
    boolean isFn
  ) {
    public @NotNull TyckResult check(@NotNull SourcePos overallPos) {
      var lhsResult = parent.checkAllLhs(computeIndices(), signature, clauses.view(), isFn);

      if (lhsResult.noneMatch(r -> r.hasError)) {
        var classes = PatClassifier.classify(lhsResult.view().map(LhsResult::clause),
          signature.params().view(), parent.exprTycker, overallPos);
        if (clauses.isNotEmpty()) {
          var usages = PatClassifier.firstMatchDomination(clauses, parent.reporter(), classes);
          // refinePatterns(lhsResults, usages, classes);
        }
      }

      return parent.checkAllRhs(vars, lhsResult);
    }
    private @Nullable ImmutableIntSeq computeIndices() {
      return elims.isEmpty() ? null : elims.mapToInt(ImmutableIntSeq.factory(),
        vars::indexOf);
    }
    public @NotNull TyckResult checkNoClassify() {
      return parent.checkAllRhs(vars, parent.checkAllLhs(computeIndices(), signature, clauses.view(), isFn));
    }
  }

  public @NotNull ImmutableSeq<LhsResult> checkAllLhs(
    @Nullable ImmutableIntSeq indices, @NotNull Signature signature,
    @NotNull SeqView<Pattern.Clause> clauses, boolean isFn
  ) {
    return clauses.map(c -> checkLhs(signature, indices, c, isFn)).toImmutableSeq();
  }

  public @NotNull TyckResult checkAllRhs(
    @NotNull ImmutableSeq<LocalVar> vars,
    @NotNull ImmutableSeq<LhsResult> lhsResults
  ) {
    var lhsError = lhsResults.anyMatch(LhsResult::hasError);
    var rhsResult = lhsResults.map(x -> checkRhs(vars, x));
    exprTycker.solveMetas();

    // inline terms in rhsResult
    rhsResult = rhsResult.map(preclause -> new Pat.Preclause<>(
      preclause.sourcePos(),
      preclause.pats().map(p -> p.descent(UnaryOperator.identity(), exprTycker::zonk)),
      preclause.bindCount(),
      preclause.expr() == null ? null : preclause.expr().descent((_, t) -> exprTycker.zonk(t))
    ));

    return new TyckResult(
      rhsResult,
      rhsResult.flatMap(Pat.Preclause::lift),
      lhsError
    );
  }

  @Override public @NotNull Reporter reporter() { return exprTycker.reporter; }
  @Override public @NotNull TyckState state() { return exprTycker.state; }
  private @NotNull PatternTycker newPatternTycker(
    @Nullable ImmutableIntSeq indices,
    @NotNull SeqView<Param> telescope
  ) {
    telescope = indices != null
      ? telescope.mapIndexed((idx, p) -> indices.contains(idx) ? p.explicitize() : p.implicitize())
      : telescope;

    return new PatternTycker(exprTycker, telescope, new LocalLet(), indices == null,
      new Renamer());
  }

  public @NotNull LhsResult checkLhs(
    @NotNull Signature signature,
    @Nullable ImmutableIntSeq indices,
    @NotNull Pattern.Clause clause,
    boolean isFn
  ) {
    var tycker = newPatternTycker(indices, signature.params().view());
    return exprTycker.subscoped(() -> {
      // If a pattern occurs in elimination environment, then we check if it contains absurd pattern.
      // If it is not the case, the pattern must be accompanied by a body.
      if (isFn && !clause.patterns.anyMatch(p -> hasAbsurdity(p.term().data())) && clause.expr.isEmpty()) {
        clause.hasError = true;
        exprTycker.fail(new PatternProblem.InvalidEmptyBody(clause));
      }

      var patResult = tycker.tyck(clause.patterns.view(), null, clause.expr.getOrNull());
      var ctx = exprTycker.localCtx();   // No need to copy the context here

      clause.hasError |= patResult.hasError();
      patResult = inline(patResult, ctx);
      var resultTerm = TermInline.apply(signature.result().instantiateTele(patResult.paramSubstObj()));
      clause.patterns.view().map(it -> it.term().data()).forEach(TermInPatInline::apply);

      // It is safe to replace ctx:
      // * telescope are well-typed and no Meta
      // * PatternTycker doesn't introduce any Meta term
      ctx = ctx.map(TermInline::apply);
      var patWithTypeBound = Pat.collectVariables(patResult.wellTyped().view());

      var allBinds = patWithTypeBound.component1().toImmutableSeq();
      var newClause = new Pat.Preclause<>(clause.sourcePos, patWithTypeBound.component2(),
        allBinds.size(), patResult.newBody());
      return new LhsResult(ctx, resultTerm, allBinds,
        patResult.wellTyped(), patResult.paramSubst(), patResult.asSubst(), newClause, patResult.hasError());
    });
  }

  /**
   * Tyck the rhs of some clause.
   *
   * @param result the tyck result of the corresponding patterns
   */
  private @NotNull Pat.Preclause<Term> checkRhs(
    @NotNull ImmutableSeq<LocalVar> teleBinds,
    @NotNull LhsResult result
  ) {
    return exprTycker.subscoped(() -> {
      var clause = result.clause;
      var bodyExpr = clause.expr();
      Term wellBody;
      var bindCount = 0;
      if (bodyExpr == null) wellBody = null;
      else if (result.hasError) {
        // In case the patterns are malformed, do not check the body
        // as we bind local variables in the pattern checker,
        // and in case the patterns are malformed, some bindings may
        // not be added to the localCtx of tycker, causing assertion errors
        wellBody = new ErrorTerm(result.clause.expr().data());
      } else {
        // the localCtx will be restored after exiting [subscoped]e
        exprTycker.setLocalCtx(result.localCtx);
        result.addLocalLet(teleBinds, exprTycker);
        // now exprTycker has all substitutions that PatternTycker introduced.
        wellBody = exprTycker.inherit(bodyExpr, result.type).wellTyped();

        // bind all pat bindings
        var patBindTele = Pat.collectVariables(result.clause.pats().view()).component1();
        bindCount = patBindTele.size();
        wellBody = wellBody.bindTele(patBindTele.view());
      }

      return new Pat.Preclause<>(clause.sourcePos(), clause.pats(), bindCount,
        wellBody == null ? null : WithPos.dummy(wellBody));
    });
  }

  private static final class TermInline {
    public static @NotNull Term apply(@NotNull Term term) {
      if (term instanceof MetaPatTerm metaPat) {
        var isEmpty = metaPat.meta().solution().isEmpty();
        if (isEmpty) throw new Panic("Unable to inline " + metaPat.toDoc(AyaPrettierOptions.debug()));
        // the solution may contain other MetaPatTerm
        return metaPat.inline(TermInline::apply);
      } else {
        return term.descent(TermInline::apply);
      }
    }

    public static @NotNull Pat apply(@NotNull Pat pat) {
      return pat.descent(TermInline::apply, TermInline::apply);
    }
  }

  private static boolean hasAbsurdity(@NotNull Pattern term) {
    return hasAbsurdity(term, MutableBooleanValue.create());
  }
  private static boolean hasAbsurdity(@NotNull Pattern term, @NotNull MutableBooleanValue b) {
    if (term == Pattern.Absurd.INSTANCE) b.set(true);
    else term.forEach((_, p) -> b.set(b.get() || hasAbsurdity(p, b)));
    return b.get();
  }

  /**
   * Inline terms which in pattern
   */
  private static final class TermInPatInline {
    public static void apply(@NotNull Pattern pat) {
      var typeRef = switch (pat) {
        case Pattern.Bind bind -> bind.type();
        case Pattern.As as -> as.type();
        default -> null;
      };

      if (typeRef != null) typeRef.update(it -> it == null ? null : TermInline.apply(it));

      pat.forEach((_, p) -> apply(p));
    }
  }

  private static @NotNull Jdg inlineTerm(@NotNull Jdg r) {
    return r.map(TermInline::apply);
  }

  /**
   * Inline terms in {@param result}, please do this after inline all patterns
   */
  private static @NotNull PatternTycker.TyckResult inline(@NotNull PatternTycker.TyckResult result, @NotNull LocalCtx ctx) {
    // inline {Pat.Meta} before inline {MetaPatTerm}s
    var wellTyped = result.wellTyped().map(x ->
      TermInline.apply(x.inline(ctx::put)));
    // so that {MetaPatTerm}s can be inlined safely
    var paramSubst = result.paramSubst().map(ClauseTycker::inlineTerm);

    // map in place 😱😱😱😱
    result.asSubst().subst().replaceAll((_, t) -> inlineTerm(t));

    return new PatternTycker.TyckResult(wellTyped, paramSubst, result.asSubst(), result.newBody(), result.hasError());
  }
}
