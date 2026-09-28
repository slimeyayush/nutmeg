package com.example.nutmeg.api.game;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/** Picks content a room hasn't seen yet; recycles once everything's been used. */
public final class Picker {

    private Picker() {}

    public static <T> T fresh(List<T> pool, Function<T, String> key, Set<String> used, Random random) {
        List<T> unused = pool.stream().filter(item -> !used.contains(key.apply(item))).toList();
        if (unused.isEmpty()) {
            pool.forEach(item -> used.remove(key.apply(item)));   // full cycle — start over
            unused = pool;
        }
        T chosen = unused.get(random.nextInt(unused.size()));
        used.add(key.apply(chosen));
        return chosen;
    }
}
