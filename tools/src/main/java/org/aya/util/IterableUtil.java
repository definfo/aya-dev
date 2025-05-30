// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

public interface IterableUtil {
  static <T> void forEach(@NotNull Iterable<T> it, @NotNull BiConsumer<T, T> separator, @NotNull Consumer<T> run) {
    var iter = it.iterator();
    if (!iter.hasNext()) return;
    var last = iter.next();
    run.accept(last);

    while (iter.hasNext()) {
      var now = iter.next();
      separator.accept(last, now);
      run.accept(now);
      last = now;
    }
  }

  static <T> void forEach(@NotNull Iterable<T> it, @NotNull Runnable separator, @NotNull Consumer<T> run) {
    forEach(it, (_, _) -> separator.run(), run);
  }
}
