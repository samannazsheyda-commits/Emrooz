package com.nameemrooz.journal.speech;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory view of user-owned spelling corrections; persistence is provided separately. */
public final class PersonalVocabulary {
    private final Map<String, String> replacements = new LinkedHashMap<>();

    public PersonalVocabulary(Map<String, String> initial) {
        if (initial != null) {
            initial.forEach(this::put);
        }
    }

    public synchronized void put(String heard, String preferred) {
        String key = normalize(heard);
        String value = normalize(preferred);
        if (!key.isEmpty() && !value.isEmpty() && !key.equals(value)) replacements.put(key, value);
    }

    public synchronized String preferredFor(String heard) {
        String key = normalize(heard);
        return replacements.getOrDefault(key, key);
    }

    public synchronized String apply(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        String result = text;
        List<Map.Entry<String, String>> ordered = new ArrayList<>(replacements.entrySet());
        ordered.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
        for (Map.Entry<String, String> entry : ordered) result = result.replace(entry.getKey(), entry.getValue());
        return result;
    }

    public synchronized Map<String, String> snapshot() {
        return Map.copyOf(replacements);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
