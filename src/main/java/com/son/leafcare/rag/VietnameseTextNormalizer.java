package com.son.leafcare.rag;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class VietnameseTextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUM = Pattern.compile("[^\\p{L}\\p{N}\\s]+");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private static final Set<String> STOP_WORDS = Set.of(
            "la", "bi", "cua", "va", "co", "nhung", "nhu", "the", "nao", "gi", "sao",
            "cho", "hoi", "cay", "toi", "em", "minh", "dang", "tren", "duoi", "lam",
            "cach", "voi", "khi", "thi", "ma", "o", "tai", "do", "nay", "kia", "rat", "qua"
    );

    private static final Map<String, String> PLANT_SYNONYMS = Map.ofEntries(
            Map.entry("ca chua", "Cà chua"),
            Map.entry("solanum lycopersicum", "Cà chua"),
            Map.entry("tomato", "Cà chua"),
            Map.entry("ot", "Ớt"),
            Map.entry("cay ot", "Ớt"),
            Map.entry("capsicum", "Ớt"),
            Map.entry("hoa hong", "Hoa hồng"),
            Map.entry("hong", "Hoa hồng"),
            Map.entry("rosa", "Hoa hồng"),
            Map.entry("lan ho diep", "Lan hồ điệp"),
            Map.entry("hoa lan", "Lan hồ điệp"),
            Map.entry("phong lan", "Lan hồ điệp"),
            Map.entry("phalaenopsis", "Lan hồ điệp"),
            Map.entry("trau ba", "Trầu bà"),
            Map.entry("epipremnum", "Trầu bà"),
            Map.entry("pothos", "Trầu bà"),
            Map.entry("dua leo", "Dưa leo"),
            Map.entry("dua chuot", "Dưa leo"),
            Map.entry("cucumis", "Dưa leo"),
            Map.entry("lua", "Lúa"),
            Map.entry("oryza", "Lúa"),
            Map.entry("ca phe", "Cà phê"),
            Map.entry("coffea", "Cà phê")
    );

    private VietnameseTextNormalizer() {}

    public static String normalizeNFC(String input) {
        if (input == null) {
            return "";
        }
        String nfc = Normalizer.normalize(input, Normalizer.Form.NFC);
        return MULTI_SPACE.matcher(nfc.trim()).replaceAll(" ");
    }

    public static String normalizeLowerNFC(String input) {
        return normalizeNFC(input).toLowerCase(Locale.forLanguageTag("vi"));
    }

    public static String stripAccents(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String nfd = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(nfd).replaceAll("");
        String dReplaced = withoutMarks.replace('đ', 'd').replace('Đ', 'd');
        String lower = dReplaced.toLowerCase(Locale.ROOT);
        String clean = NON_ALPHANUM.matcher(lower).replaceAll(" ");
        return MULTI_SPACE.matcher(clean.trim()).replaceAll(" ");
    }

    public static List<String> tokenizeKeywords(String input) {
        String ascii = stripAccents(input);
        if (ascii.isEmpty()) {
            return List.of();
        }
        String[] parts = ascii.split(" ");
        Set<String> tokens = new LinkedHashSet<>();
        for (String p : parts) {
            if (p.length() >= 2 && !STOP_WORDS.contains(p)) {
                tokens.add(p);
            }
        }
        return new ArrayList<>(tokens);
    }

    public static String detectPlantTag(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String ascii = " " + stripAccents(text) + " ";
        // Check longer phrases first
        List<Map.Entry<String, String>> entries = new ArrayList<>(PLANT_SYNONYMS.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        for (Map.Entry<String, String> entry : entries) {
            if (ascii.contains(" " + entry.getKey() + " ")) {
                return entry.getValue();
            }
        }
        return null;
    }
}

