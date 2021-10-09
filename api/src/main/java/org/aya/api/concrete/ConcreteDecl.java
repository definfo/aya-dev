// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.concrete;

import org.aya.api.core.CoreDef;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public interface ConcreteDecl {
  @Contract(pure = true) @NotNull DefVar<? extends CoreDef, ? extends ConcreteDecl> ref();
  @Contract(pure = true) @NotNull SourcePos sourcePos();
}
