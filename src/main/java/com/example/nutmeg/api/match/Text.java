package com.example.nutmeg.api.match;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Text {

    private Text() {}

    /**
     * One canonical form for everything.
     *   "Sergio Agüero"  -> "sergio aguero"
     *   "  MAN UTD. "    -> "man utd"
     * NFD splits "ü" into "u" + a combining mark; \p{M} deletes the mark.
     */
    public static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("ø", "o").replace("Ø", "o")
                .replace("ß", "ss")
                .replace("ł", "l").replace("Ł", "l")
                .replace("đ", "d").replace("Đ", "d")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    /** Last token. "sergio aguero" -> "aguero". */
    public static String surname(String normalised) {
        int space = normalised.lastIndexOf(' ');
        return space < 0 ? normalised : normalised.substring(space + 1);
    }

    /**
     * Every normalised string that should count as this answer:
     * the full name, the surname (people only), and any hand-written aliases.
     */
    public static Set<String> keysFor(String display, List<String> aliases, boolean person) {
        Set<String> keys = new LinkedHashSet<>();
        String full = normalise(display);
        keys.add(full);
        if (person) {
            String surname = surname(full);
            if (surname.length() >= 4 && !surname.equals(full)) {
                keys.add(surname);
            }
        }
        for (String alias : aliases) {
            String n = normalise(alias);
            if (!n.isEmpty()) {
                keys.add(n);
            }
        }
        return Set.copyOf(keys);
    }

    /**
     * Levenshtein distance with two rows and an early exit.
     * Only whether it's <= max matters, so once a whole row exceeds max we stop.
     *
     * @return the distance, or max + 1 meaning "further than we care about"
     */
    public static int editDistance(String a, String b, int max) {
        if (a.equals(b)) {
            return 0;
        }
        if (Math.abs(a.length() - b.length()) > max) {
            return max + 1;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return Math.max(a.length(), b.length());
        }

        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** Typos forgiven, by length. Short strings get none: "kane" vs "kean". */
    public static int tolerance(String normalised) {
        int len = normalised.length();
        if (len < 5) {
            return 0;
        }
        if (len < 9) {
            return 1;
        }
        return 2;
    }
}
