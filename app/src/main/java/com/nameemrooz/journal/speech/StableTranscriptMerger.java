package com.nameemrooz.journal.speech;

import java.util.Arrays;

/** Keeps committed speech stable while allowing only the active utterance to change. */
public final class StableTranscriptMerger {
    private String committed = "";
    private String partial = "";

    public synchronized String updatePartial(String candidate) {
        partial = normalize(candidate);
        return render();
    }

    public synchronized String commit(String finalUtterance) {
        String incoming = normalize(finalUtterance);
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

        String[] leftWords = left.split(" ");
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
        return word.replaceAll("[،,.!؟?:؛;]+$", "");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
