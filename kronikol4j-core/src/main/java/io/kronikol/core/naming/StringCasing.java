package io.kronikol.core.naming;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * String casing utilities for formatting scenario and feature display names. Java port of the .NET
 * {@code StringCasing} (itself ported from Humanizer under the MIT License). Lives in {@code kronikol4j-core}
 * — the zero-dependency home matching .NET's core {@code Kronikol} namespace — so every test-framework
 * adapter ({@link ScenarioTitleResolver}) and the report module share one implementation.
 *
 * <p>Parity note: .NET uses {@code CultureInfo.CurrentCulture.TextInfo.ToTitleCase}; this hand-rolled
 * {@link #toTitleCase} reproduces the observable behaviour (capitalise each whitespace-delimited word,
 * lower the rest, leave all-uppercase acronyms untouched) without depending on the host locale.
 */
public final class StringCasing {

    // From Humanizer's StringHumanizeExtensions.
    private static final Pattern PASCAL_CASE_WORD_PARTS = Pattern.compile(
        "(\\p{Lu}?\\p{Ll}+|[0-9]+\\p{Ll}*|\\p{Lu}+(?=\\p{Lu}|[0-9]|\\b)|\\p{Lo}+)[,;]?");

    private StringCasing() {
    }

    /** Equivalent to Humanizer's {@code Titleize()} (Humanize + ToTitleCase). */
    public static String titleize(String input) {
        String humanized = humanize(input);
        return humanized.isEmpty() ? input : toTitleCase(humanized);
    }

    private static String humanize(String input) {
        if (allUpper(input)) {
            return input;
        }
        if (input.indexOf('_') >= 0 || input.indexOf('-') >= 0) {
            return fromPascalCase(input.replace('_', ' ').replace('-', ' '));
        }
        return fromPascalCase(input);
    }

    private static String fromPascalCase(String input) {
        Matcher m = PASCAL_CASE_WORD_PARTS.matcher(input);
        List<String> parts = new ArrayList<>();
        while (m.find()) {
            String value = m.group();
            boolean keep = allUpper(value)
                && (value.length() > 1 || (m.start() > 0 && input.charAt(m.start() - 1) == ' ') || value.equals("I"));
            parts.add(keep ? value : value.toLowerCase(Locale.ROOT));
        }
        String result = String.join(" ", parts);
        if (allSpaceOrUpper(result) && result.contains(" ")) {
            result = result.toLowerCase(Locale.ROOT);
        }
        return result.isEmpty() ? result
            : Character.toUpperCase(result.charAt(0)) + result.substring(1);
    }

    /** Port of {@code TextInfo.ToTitleCase}: capitalise each word, lower the rest, but leave
     *  all-uppercase words (acronyms) untouched; word boundaries are whitespace. */
    private static String toTitleCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append(c);
                i++;
                continue;
            }
            int start = i;
            while (i < s.length() && !Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            String word = s.substring(start, i);
            if (allUpper(word)) {
                sb.append(word); // acronym
            } else {
                sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    private static boolean allUpper(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isUpperCase(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean allSpaceOrUpper(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && !Character.isUpperCase(c)) {
                return false;
            }
        }
        return true;
    }
}
