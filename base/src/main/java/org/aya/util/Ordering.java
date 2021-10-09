// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public enum Ordering {
  Gt(">="), Eq("=="), Lt("<=");

  public final @NotNull String symbol;

  Ordering(@NotNull String symbol) {
    this.symbol = symbol;
  }
}
