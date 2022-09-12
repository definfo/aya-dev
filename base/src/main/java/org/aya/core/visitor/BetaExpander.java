// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.generic.Cube;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * We think of all cubical reductions as beta reductions.
 *
 * @author wsx
 * @see DeltaExpander
 */
public interface BetaExpander extends EndoFunctor {
  static @NotNull IntroTerm.PartEl partial(@NotNull IntroTerm.PartEl el) {
    return new IntroTerm.PartEl(partial(el.partial()), el.rhsType());
  }
  private static @NotNull Partial<Term> partial(@NotNull Partial<Term> partial) {
    return partial.flatMap(Function.identity());
  }
  static @NotNull Term pathApp(@NotNull ElimTerm.PathApp app, @NotNull Function<Term, Term> next) {
    if (app.of() instanceof IntroTerm.PathLam lam) {
      var xi = lam.params().map(Term.Param::ref);
      var ui = app.args().map(Arg::term);
      var subst = new Subst(xi, ui);
      return next.apply(lam.body().subst(subst));
    }
    return switch (partial(app.cube().partial())) {
      case Partial.Split<Term> hap -> new ElimTerm.PathApp(app.of(), app.args(), new Cube<>(
        app.cube().params(), app.cube().type(), hap));
      case Partial.Const<Term> sad -> sad.u();
    };
  }
  static @NotNull Term simplFormula(@NotNull PrimTerm.Mula mula) {
    return Restr.formulae(mula.asFormula(), PrimTerm.Mula::new);
  }
  static @NotNull FormTerm.PartTy partialType(@NotNull FormTerm.PartTy ty) {
    return new FormTerm.PartTy(ty.type(), ty.restr().normalize());
  }
  @Override default @NotNull Term post(@NotNull Term term) {
    return switch (term) {
      case PrimTerm.Mula mula -> simplFormula(mula);
      case FormTerm.PartTy ty -> partialType(ty);
      case RefTerm.MetaPat metaPat -> metaPat.inline();
      case ElimTerm.App app -> {
        var result = CallTerm.make(app);
        yield result == term ? result : apply(result);
      }
      case ElimTerm.Proj proj -> ElimTerm.proj(proj);
      case ElimTerm.PathApp app -> pathApp(app, this);
      case IntroTerm.PartEl partial -> partial(partial);
      default -> term;
    };
  }
}