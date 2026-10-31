package com.nameemrooz.journal.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic, offline cleanup. It never paraphrases or sends text off device. */
public final class PersianEditorial {
    private PersianEditorial() {}

    private static final Set<String> FILLERS = Set.of(
            "اِم", "اِمم", "ام", "امم", "اممم", "اا", "آآ", "آآآ", "اه", "هوم", "هومم", "اوه"
    );

    private static final Map<String, String> PHRASE_FIXES = new LinkedHashMap<>();
    static {
        PHRASE_FIXES.put("نمی خواستم", "نمی‌خواستم"); PHRASE_FIXES.put("نمیخواستم", "نمی‌خواستم");
        PHRASE_FIXES.put("می خواستم", "می‌خواستم"); PHRASE_FIXES.put("میخواستم", "می‌خواستم");
        PHRASE_FIXES.put("نمی دونم", "نمی‌دونم"); PHRASE_FIXES.put("نمیدونم", "نمی‌دونم");
        PHRASE_FIXES.put("می دونم", "می‌دونم"); PHRASE_FIXES.put("میدونم", "می‌دونم");
        PHRASE_FIXES.put("نمی تونم", "نمی‌تونم"); PHRASE_FIXES.put("نمیتونم", "نمی‌تونم");
        PHRASE_FIXES.put("می تونم", "می‌تونم"); PHRASE_FIXES.put("میتونم", "می‌تونم");
        PHRASE_FIXES.put("نمی شه", "نمی‌شه"); PHRASE_FIXES.put("نمیشه", "نمی‌شه");
        PHRASE_FIXES.put("می شه", "می‌شه"); PHRASE_FIXES.put("میشه", "می‌شه");
        PHRASE_FIXES.put("می خوام", "می‌خوام"); PHRASE_FIXES.put("میخوام", "می‌خوام");
        PHRASE_FIXES.put("نمی کنم", "نمی‌کنم"); PHRASE_FIXES.put("نمیکنم", "نمی‌کنم");
        PHRASE_FIXES.put("می کنم", "می‌کنم"); PHRASE_FIXES.put("میکنم", "می‌کنم");
        PHRASE_FIXES.put("می رم", "می‌رم"); PHRASE_FIXES.put("میرم", "می‌رم");
    }

    private static final Pattern PROTECTED_TOKEN = Pattern.compile(
            "(?i)[0-9۰-۹٠-٩]+(?:[./:٫٬,_-][0-9۰-۹٠-٩]+)*|(?:https?://|www\\.)\\S+|[A-Za-z0-9]+(?:[._:/\\\\@#%+&=\\-][A-Za-z0-9]+)+|[A-Za-z][A-Za-z0-9]*"
    );
    private static final char MARKER_START = '\uE100';
    private static final char MARKER_END = '\uE101';

    public static String clean(String input, boolean isFinal) {
        if (input == null || input.isBlank()) return "";
        Protected protectedText = protect(input);
        String text = normalizeLetters(protectedText.text);
        for (Map.Entry<String, String> fix : PHRASE_FIXES.entrySet()) {
            text = text.replaceAll("(?<![\\p{L}\\p{N}\\u200c])" + Pattern.quote(fix.getKey()) + "(?![\\p{L}\\p{N}\\u200c])", Matcher.quoteReplacement(fix.getValue()));
        }
        text = normalizePunctuation(text);
        text = removeFillersAndAccidentalRepeats(text);
        text = restore(text, protectedText.tokens).trim();
        if (isFinal && !text.isEmpty() && ".!؟".indexOf(text.charAt(text.length() - 1)) < 0) {
            text += ".";
        }
        return text;
    }

    public static String applyReplacements(String input, Map<String, String> replacements) {
        if (input == null || input.isEmpty() || replacements == null || replacements.isEmpty()) return input == null ? "" : input;
        String result = input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String from = entry.getKey() == null ? "" : entry.getKey().trim();
            String to = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!from.isEmpty() && !to.isEmpty()) result = result.replace(from, to);
        }
        return result;
    }

    private static String normalizeLetters(String value) {
        return value.replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
                .replace('ة', 'ه').replace('ۀ', 'ه').replace('\u200e', ' ').replace('\u200f', ' ')
                .replaceAll("[\\t\\n\\r]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static String normalizePunctuation(String value) {
        return value.replace(',', '،').replace('?', '؟').replace(';', '؛')
                .replaceAll("\\s+([،؛:؟!.])", "$1")
                .replaceAll("([،؛:؟!.])(?=\\S)", "$1 ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String removeFillersAndAccidentalRepeats(String value) {
        List<String> result = new ArrayList<>();
        for (String word : value.split(" ")) {
            if (word.isBlank()) continue;
            String bare = stripPunctuation(word);
            if (bare.isEmpty() || FILLERS.contains(bare)) continue;
            String previous = result.isEmpty() ? "" : stripPunctuation(result.get(result.size() - 1));
            // Keep emphasis. ASR overlap deduplication belongs to StableTranscriptMerger.
            result.add(word);
        }
        return String.join(" ", result);
    }

    private static String stripPunctuation(String value) {
        return value.replaceAll("^[،,.!؟?:؛;]+|[،,.!؟?:؛;]+$", "");
    }

    private static Protected protect(String source) {
        Matcher matcher = PROTECTED_TOKEN.matcher(source);
        StringBuffer out = new StringBuffer();
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            int index = tokens.size();
            tokens.add(matcher.group());
            String marker = "" + MARKER_START + Integer.toString(index, 36).toUpperCase(Locale.ROOT) + MARKER_END;
            matcher.appendReplacement(out, Matcher.quoteReplacement(marker));
        }
        matcher.appendTail(out);
        return new Protected(out.toString(), tokens);
    }

    private static String restore(String source, List<String> tokens) {
        String result = source;
        for (int index = 0; index < tokens.size(); index++) {
            String marker = "" + MARKER_START + Integer.toString(index, 36).toUpperCase(Locale.ROOT) + MARKER_END;
            result = result.replace(marker, tokens.get(index));
        }
        return result;
    }

    private record Protected(String text, List<String> tokens) {}
}
