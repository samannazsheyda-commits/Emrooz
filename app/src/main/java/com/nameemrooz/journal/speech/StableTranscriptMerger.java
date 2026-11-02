package com.nameemrooz.journal.speech;

import java.util.Arrays;
import java.util.regex.Pattern;

/** Keeps committed speech stable while allowing only the active utterance to change. */
public final class StableTranscriptMerger {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NOISE = Pattern.compile("[\\t\\n\\r\\x0B\\f]| {2,}");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[،,.!؟?:؛;]+$");
    private String committed = "";
    private String partial = "";

    public synchronized String updatePartial(String candidate) {
        partial = normalize(candidate);
        return render();
    }

    public synchronized String commit(String finalUtterance) {
        String incoming = normalize(finalUtterance);
        if (incoming.isEmpty()) incoming = partial;
        if (!incoming.isEmpty()) committed = mergeWithOverlap(committed, incoming);
        partial = "";
        return committed;
    }

    public synchronized String current() {
        return render();
    }

    public synchronized void reset() {
        committed = "";
        partial = "";
    }

    private String render() {
        if (partial.isEmpty()) return committed;
        return mergeWithOverlap(committed, partial);
    }

    static String mergeWithOverlap(String left, String right) {
        left = normalize(left);
        right = normalize(right);
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;

        int tailStart = left.length();
        for (int count=0; count<12; count++) {
            int boundary = left.lastIndexOf(' ', tailStart - 1);
            if (boundary < 0) { tailStart=0; break; }
            tailStart=boundary;
        }
        String[] leftWords = left.substring(tailStart == 0 ? 0 : tailStart+1).split(" ");
        String[] rightWords = right.split(" ");
        int max = Math.min(Math.min(leftWords.length, rightWords.length), 12);
        int overlap = 0;
        for (int size = max; size > 0; size--) {
            boolean equal = true;
            for (int offset = 0; offset < size; offset++) {
                String a = comparable(leftWords[leftWords.length - size + offset]);
                String b = comparable(rightWords[offset]);
                if (!a.equals(b)) { equal = false; break; }
            }
            if (equal) { overlap = size; break; }
        }
        String suffix = String.join(" ", Arrays.copyOfRange(rightWords, overlap, rightWords.length));
        return suffix.isEmpty() ? left : left + " " + suffix;
    }

    private static String comparable(String word) {
        return TRAILING_PUNCTUATION.matcher(word).replaceAll("");
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        return NOISE.matcher(trimmed).find() ? WHITESPACE.matcher(trimmed).replaceAll(" ") : trimmed;
    }
}
