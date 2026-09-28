package com.example.nutmeg.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.nutmeg.api.match.AnswerMatcher.Outcome;

class AnswerMatcherTest {

    record Item(String name, Set<String> keys) implements AnswerMatcher.Keyed {
        static Item of(String name, String... aliases) {
            return new Item(name, Text.keysFor(name, List.of(aliases), true));
        }
    }

    private final AnswerMatcher matcher = new AnswerMatcher();

    private final List<Item> pool = List.of(
            Item.of("Alan Shearer"),
            Item.of("Harry Kane"),
            Item.of("Sergio Agüero"),
            Item.of("Cristiano Ronaldo", "cr7", "cristiano"),
            Item.of("Ronaldo Nazario", "ronaldo", "r9"));

    private String hit(String guess) {
        var result = matcher.match(guess, pool);
        assertEquals(Outcome.HIT, result.outcome(), "expected a hit for " + guess);
        return result.single().name();
    }

    @Test void exactFullName()        { assertEquals("Harry Kane", hit("Harry Kane")); }
    @Test void surnameAlone()         { assertEquals("Harry Kane", hit("KANE")); }
    @Test void accentsStripped()      { assertEquals("Sergio Agüero", hit("aguero")); }
    @Test void typoWithinBudget()     { assertEquals("Sergio Agüero", hit("Sergio Aguerro")); }
    @Test void nickname()             { assertEquals("Cristiano Ronaldo", hit("cr7")); }
    @Test void nicknameDisambiguates(){ assertEquals("Ronaldo Nazario", hit("r9")); }
    @Test void punctuationIgnored()   { assertEquals("Alan Shearer", hit("  alan   SHEARER!! ")); }

    @Test
    void shortWordsGetNoTypoTolerance() {
        assertEquals(Outcome.MISS, matcher.match("kean", pool).outcome());
    }

    @Test
    void sharedSurnameIsAmbiguous() {
        var result = matcher.match("ronaldo", pool);
        assertEquals(Outcome.AMBIGUOUS, result.outcome());
        assertEquals(2, result.matches().size());
    }

    @Test
    void nonsenseMisses() {
        assertEquals(Outcome.MISS, matcher.match("zinedine zidane", pool).outcome());
        assertEquals(Outcome.MISS, matcher.match("", pool).outcome());
        assertEquals(Outcome.MISS, matcher.match(null, pool).outcome());
    }

    @Test
    void editDistanceIsCapped() {
        assertEquals(0, Text.editDistance("kane", "kane", 2));
        assertEquals(1, Text.editDistance("kane", "kan", 2));
        assertEquals(3, Text.editDistance("shearer", "kane", 2));   // max + 1 = "too far"
    }
}
