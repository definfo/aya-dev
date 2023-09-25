// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.core.def.GenericDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.repr.CodeShape;
import org.aya.core.repr.ShapeMatcher;
import org.aya.core.repr.ShapeRecognition;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.ref.DefVar;
import org.aya.tyck.TyckDeclTest;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("UnknownLanguage")
public class ShapeMatcherTest {
  @Test
  public void matchNat() {
    match(true, AyaShape.NAT_SHAPE, "open data Nat | zero | suc Nat");
    match(true, AyaShape.NAT_SHAPE, "open data Nat | suc Nat | zero");
    match(true, AyaShape.NAT_SHAPE, "open data Nat | z | s Nat");

    match(ImmutableSeq.of(Tuple.of(true, AyaShape.NAT_SHAPE), Tuple.of(false, AyaShape.NAT_SHAPE)), """
      open data Nat | zero | suc Nat
      open data Fin (n : Nat) | suc n => fzero | suc n => fsuc (Fin n)
      """);

    match(false, AyaShape.NAT_SHAPE, "open data Nat | s | z");
  }

  @Test
  public void matchList() {
    match(true, AyaShape.LIST_SHAPE, "data List (A : Prop) | nil | cons A (List A)");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons A (List A)");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | cons A (List A) | nil");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | infixr :< A (List A)");

    match(false, AyaShape.LIST_SHAPE, "data List | nil | cons");
    match(false, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons");
    match(false, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons A A");
    match(false, AyaShape.LIST_SHAPE, "data List (A : Type) | nil A | cons A (List A)");
    match(false, AyaShape.LIST_SHAPE, "data List (A B : Type) | nil | cons A A");
    match(false, AyaShape.LIST_SHAPE, "data List (A B : Type) | nil | cons A B");
    match(false, AyaShape.LIST_SHAPE, """
      data False
      data List (A : Type)
        | nil
        | cons A (List False)
      """);
  }

  @Test
  public void capture() {
    var match = match(true, AyaShape.NAT_SHAPE, "open data Nat | zero | suc (pred : Nat)");
    assertNotNull(match);
    assertEquals("| zero", pp(match.captures().get(CodeShape.MomentId.ZERO)));
    assertEquals("| suc (pred : Nat)", pp(match.captures().get(CodeShape.MomentId.SUC)));
    assertNull(match.captures().getOrNull(CodeShape.MomentId.NIL));
    assertNull(match.captures().getOrNull(CodeShape.MomentId.CONS));

    match = match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | infixr :< (a : A) (tail : List A)");
    assertNotNull(match);
    assertEquals("| nil", pp(match.captures().get(CodeShape.MomentId.NIL)));
    assertEquals("| :< (a : A) (tail : List A)", pp(match.captures().get(CodeShape.MomentId.CONS)));
    assertNull(match.captures().getOrNull(CodeShape.MomentId.ZERO));
    assertNull(match.captures().getOrNull(CodeShape.MomentId.SUC));
  }

  private @NotNull String pp(@NotNull DefVar<?, ?> def) {
    return def.core.toDoc(AyaPrettierOptions.pretty()).debugRender();
  }

  @Test
  public void matchWeirdList() {
    match(true, AyaShape.LIST_SHAPE, "data List {A : Type} | nil | cons A (List {A})");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons {A} (List A)");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons A {List A}");
    match(true, AyaShape.LIST_SHAPE, "data List {A : Type} | nil | cons {A} (List {A})");
    match(true, AyaShape.LIST_SHAPE, "data List {A : Type} | nil | cons A {List {A}}");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons {A} {List A}");
    match(true, AyaShape.LIST_SHAPE, "data List {A : Type} | nil | cons {A} {List {A}}");
  }

  @Test
  public void matchPlus() {
    match(ImmutableSeq.of(
      Tuple.of(true, AyaShape.NAT_SHAPE),
      Tuple.of(true, AyaShape.AyaPlusFnShape.INSTANCE)
    ), """
      open data Nat | zero | suc Nat
      def plus Nat Nat : Nat
      | left, zero => left
      | left, suc right => plus (suc left) right
      """);
  }

  public @Nullable ShapeRecognition match(boolean should, @NotNull AyaShape shape, @Language("Aya") @NonNls @NotNull String code) {
    var def = TyckDeclTest.successTyckDecls(code).component2();
    return check(ImmutableSeq.fill(def.size(), Tuple.of(should, shape)), def).firstOrNull();
  }

  public void match(@NotNull ImmutableSeq<Tuple2<Boolean, AyaShape>> shouldBe, @Language("Aya") @NonNls @NotNull String code) {
    var def = TyckDeclTest.successTyckDecls(code).component2();
    check(shouldBe, def);
  }

  private static ImmutableSeq<ShapeRecognition> check(@NotNull ImmutableSeq<Tuple2<Boolean, AyaShape>> shouldBe, @NotNull ImmutableSeq<GenericDef> def) {
    var discovered = MutableMap.<GenericDef, ShapeRecognition>create();

    return def.zipView(shouldBe).flatMap(tup -> {
      var should = tup.component2().component1();
      var shape = tup.component2().component2();
      var match = ShapeMatcher.match(new ShapeMatcher(discovered.toImmutableMap()), shape, tup.component1());
      assertEquals(should, match.isDefined());
      if (should) {
        discovered.put(tup.component1(), match.get());
      }
      return match;
    }).toImmutableSeq();
  }
}
