package com.example.nutmeg.api.match;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Matches free text against a pool of answers.
 * Three outcomes, never two: exactly one, several (ask again), or none.
 */
public final class AnswerMatcher {

    /** Anything that can be matched: a TEN slot, a Heads Up card, ... */
    public interface Keyed {
        Set<String> keys();
    }

    public enum Outcome { HIT, AMBIGUOUS, MISS }

    public record Result<T extends Keyed>(Outcome outcome, List<T> matches) {

        public static <T extends Keyed> Result<T> miss() {
            return new Result<>(Outcome.MISS, List.of());
        }

        public T single() {
            return matches.get(0);
        }
    }

    /** Stage 1 only: exact hit on any key. */
    public <T extends Keyed> Result<T> exact(String normalisedGuess, Collection<T> pool) {
        List<T> hits = new ArrayList<>();
        for (T item : pool) {
            if (item.keys().contains(normalisedGuess)) {
                hits.add(item);
            }
        }
        return toResult(hits);
    }

    /** Stage 2 only: closest key within the length-scaled typo budget. */
    public <T extends Keyed> Result<T> fuzzy(String normalisedGuess, Collection<T> pool) {
        int budget = Text.tolerance(normalisedGuess);
        if (budget == 0) {
            return Result.miss();
        }
        int best = Integer.MAX_VALUE;
        List<T> bestMatches = new ArrayList<>();

        for (T item : pool) {
            // Minimum across ONE item's keys first, so an item never ties with itself.
            int distance = Integer.MAX_VALUE;
            for (String key : item.keys()) {
                distance = Math.min(distance, Text.editDistance(normalisedGuess, key, budget));
            }
            if (distance > budget) {
                continue;
            }
            if (distance < best) {
                best = distance;
                bestMatches.clear();
                bestMatches.add(item);
            } else if (distance == best) {
                bestMatches.add(item);
            }
        }
        return toResult(bestMatches);
    }

    /** Both stages: exact wins outright, fuzzy only if exact found nothing. */
    public <T extends Keyed> Result<T> match(String rawGuess, Collection<T> pool) {
        String guess = Text.normalise(rawGuess);
        if (guess.isEmpty() || pool.isEmpty()) {
            return Result.miss();
        }
        Result<T> exact = exact(guess, pool);
        if (exact.outcome() != Outcome.MISS) {
            return exact;
        }
        return fuzzy(guess, pool);
    }

    private static <T extends Keyed> Result<T> toResult(List<T> hits) {
        if (hits.isEmpty()) {
            return Result.miss();
        }
        if (hits.size() == 1) {
            return new Result<>(Outcome.HIT, List.copyOf(hits));
        }
        return new Result<>(Outcome.AMBIGUOUS, List.copyOf(hits));
    }
}
