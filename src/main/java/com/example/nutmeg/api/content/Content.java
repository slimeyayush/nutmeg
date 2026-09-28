package com.example.nutmeg.api.content;

import java.util.List;
import java.util.Set;

import com.example.nutmeg.api.match.AnswerMatcher;

/** Everything the games read, loaded once at startup from the XML files. */
public record Content(
        List<List<String>> imposterClusters,
        List<TenList> tenLists,
        List<HolCategory> holCategories,
        List<Card> headsUpCards
) {

    /** One TEN board: a question with exactly ten answers. */
    public record TenList(String question, List<TenAnswer> answers) {}

    /**
     * @param points 1..10, how obscure it is. 1 = everyone gets it, 10 = deep cut.
     */
    public record TenAnswer(String display, int points, Set<String> keys)
            implements AnswerMatcher.Keyed {}

    /** A Higher or Lower category, e.g. "Premier League goals". */
    public record HolCategory(
            String label,
            String question,
            String higherWord,
            String lowerWord,
            List<HolItem> items) {}

    public record HolItem(String name, long value) {}

    /** A Heads Up card. */
    public record Card(String name, String kind, Set<String> keys)
            implements AnswerMatcher.Keyed {}
}
