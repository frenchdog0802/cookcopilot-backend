package com.lardermind.unit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits free-text recipe ingredient lines into name / quantity / unit / note.
 * Handles common Chinese (iCook-style) and English patterns.
 */
public final class IngredientLineParser {

    public record ParsedIngredient(String name, String quantity, String unit, String note) {}

    /** Longer Chinese units first so 大匙 wins over shorter tokens. */
    private static final List<Map.Entry<String, String>> CHINESE_UNITS = List.of(
            Map.entry("大匙", UnitConverter.TBSP),
            Map.entry("湯匙", UnitConverter.TBSP),
            Map.entry("小匙", UnitConverter.TSP),
            Map.entry("茶匙", UnitConverter.TSP),
            Map.entry("公斤", UnitConverter.KG),
            Map.entry("公克", UnitConverter.G),
            Map.entry("毫升", UnitConverter.ML),
            Map.entry("公升", UnitConverter.L),
            Map.entry("杯", UnitConverter.CUP),
            Map.entry("克", UnitConverter.G),
            Map.entry("升", UnitConverter.L),
            Map.entry("顆", UnitConverter.PCS),
            Map.entry("個", UnitConverter.PCS),
            Map.entry("塊", UnitConverter.PCS),
            Map.entry("條", UnitConverter.PCS),
            Map.entry("根", UnitConverter.PCS),
            Map.entry("片", UnitConverter.SLICE),
            Map.entry("瓣", UnitConverter.CLOVE),
            Map.entry("罐", UnitConverter.CAN),
            Map.entry("把", UnitConverter.BUNCH),
            Map.entry("束", UnitConverter.BUNCH)
    );

    private static final List<Map.Entry<String, String>> ENGLISH_UNITS = List.of(
            Map.entry("tablespoons", UnitConverter.TBSP),
            Map.entry("tablespoon", UnitConverter.TBSP),
            Map.entry("teaspoons", UnitConverter.TSP),
            Map.entry("teaspoon", UnitConverter.TSP),
            Map.entry("tbsps", UnitConverter.TBSP),
            Map.entry("tbsp", UnitConverter.TBSP),
            Map.entry("tsps", UnitConverter.TSP),
            Map.entry("tsp", UnitConverter.TSP),
            Map.entry("cups", UnitConverter.CUP),
            Map.entry("cup", UnitConverter.CUP),
            Map.entry("grams", UnitConverter.G),
            Map.entry("gram", UnitConverter.G),
            Map.entry("kilograms", UnitConverter.KG),
            Map.entry("kilogram", UnitConverter.KG),
            Map.entry("ounces", UnitConverter.OZ),
            Map.entry("ounce", UnitConverter.OZ),
            Map.entry("pounds", UnitConverter.LB),
            Map.entry("pound", UnitConverter.LB),
            Map.entry("milliliters", UnitConverter.ML),
            Map.entry("millilitres", UnitConverter.ML),
            Map.entry("milliliter", UnitConverter.ML),
            Map.entry("millilitre", UnitConverter.ML),
            Map.entry("liters", UnitConverter.L),
            Map.entry("litres", UnitConverter.L),
            Map.entry("liter", UnitConverter.L),
            Map.entry("litre", UnitConverter.L),
            Map.entry("pieces", UnitConverter.PCS),
            Map.entry("piece", UnitConverter.PCS),
            Map.entry("cloves", UnitConverter.CLOVE),
            Map.entry("clove", UnitConverter.CLOVE),
            Map.entry("slices", UnitConverter.SLICE),
            Map.entry("slice", UnitConverter.SLICE),
            Map.entry("stalks", UnitConverter.STALK),
            Map.entry("stalk", UnitConverter.STALK),
            Map.entry("bunches", UnitConverter.BUNCH),
            Map.entry("bunch", UnitConverter.BUNCH),
            Map.entry("cans", UnitConverter.CAN),
            Map.entry("can", UnitConverter.CAN),
            Map.entry("pcs", UnitConverter.PCS),
            Map.entry("pc", UnitConverter.PCS),
            Map.entry("kg", UnitConverter.KG),
            Map.entry("g", UnitConverter.G),
            Map.entry("ml", UnitConverter.ML),
            Map.entry("oz", UnitConverter.OZ),
            Map.entry("lb", UnitConverter.LB),
            Map.entry("l", UnitConverter.L)
    );

    private static final String QUANTITY =
            "(?<qty>\\d+(?:\\.\\d+)?(?:\\s*/\\s*\\d+(?:\\.\\d+)?)?)";

    private static final Pattern BRACKET_PREFIX = Pattern.compile("^\\[[^\\]]*\\]\\s*");
    private static final Pattern TO_TASTE = Pattern.compile(
            "^(?<name>.+?)\\s*(?<note>少許|適量|to\\s+taste|a\\s+pinch)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CHINESE_TRAILING = buildChineseTrailingPattern();
    private static final Pattern ENGLISH_LEADING = buildEnglishLeadingPattern();

    private IngredientLineParser() {}

    public static ParsedIngredient parse(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return new ParsedIngredient("", "", "", "");
        }

        String line = rawLine.trim();
        StringBuilder noteParts = new StringBuilder();

        Matcher bracket = BRACKET_PREFIX.matcher(line);
        if (bracket.find()) {
            String bracketText = bracket.group().trim();
            if (bracketText.startsWith("[") && bracketText.endsWith("]")) {
                appendNote(noteParts, bracketText.substring(1, bracketText.length() - 1).trim());
            }
            line = line.substring(bracket.end()).trim();
        }

        Matcher toTaste = TO_TASTE.matcher(line);
        if (toTaste.matches()) {
            appendNote(noteParts, toTaste.group("note").trim());
            return new ParsedIngredient(
                    cleanName(toTaste.group("name")),
                    "",
                    "",
                    noteParts.toString());
        }

        Matcher chinese = CHINESE_TRAILING.matcher(line);
        if (chinese.matches()) {
            return new ParsedIngredient(
                    cleanName(chinese.group("name")),
                    normalizeQuantity(chinese.group("qty")),
                    canonicalizeChineseUnit(chinese.group("unit")),
                    noteParts.toString());
        }

        Matcher english = ENGLISH_LEADING.matcher(line);
        if (english.matches()) {
            return new ParsedIngredient(
                    cleanName(english.group("name")),
                    normalizeQuantity(english.group("qty")),
                    canonicalizeEnglishUnit(english.group("unit")),
                    noteParts.toString());
        }

        return new ParsedIngredient(cleanName(line), "", "", noteParts.toString());
    }

    public static Map<String, String> toMap(String rawLine) {
        ParsedIngredient parsed = parse(rawLine);
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", parsed.name());
        map.put("quantity", parsed.quantity());
        map.put("unit", parsed.unit());
        map.put("note", parsed.note());
        return map;
    }

    private static Pattern buildChineseTrailingPattern() {
        String units = CHINESE_UNITS.stream()
                .map(e -> Pattern.quote(e.getKey()))
                .reduce((a, b) -> a + "|" + b)
                .orElse("大匙");
        // name ... [約] qty [spaces] unit
        String regex = "^(?<name>.+?)\\s*約?\\s*" + QUANTITY + "\\s*(?<unit>" + units + ")\\s*$";
        return Pattern.compile(regex);
    }

    private static Pattern buildEnglishLeadingPattern() {
        String units = ENGLISH_UNITS.stream()
                .map(e -> Pattern.quote(e.getKey()))
                .reduce((a, b) -> a + "|" + b)
                .orElse("tbsp");
        String regex = "^約?\\s*" + QUANTITY + "\\s*(?<unit>" + units + ")\\s+(?<name>.+)$";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static String normalizeQuantity(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("\\s+", "");
        if (cleaned.contains("/")) {
            String[] parts = cleaned.split("/");
            try {
                double num = Double.parseDouble(parts[0]);
                double den = Double.parseDouble(parts[1]);
                if (den == 0) {
                    return cleaned;
                }
                double value = num / den;
                if (Math.abs(value - Math.rint(value)) < 1e-9) {
                    return String.valueOf((long) Math.rint(value));
                }
                return trimTrailingZeros(value);
            } catch (NumberFormatException ex) {
                return cleaned;
            }
        }
        return cleaned;
    }

    private static String trimTrailingZeros(double value) {
        String s = String.format(Locale.ROOT, "%.4f", value);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s{2,}", " ");
    }

    private static void appendNote(StringBuilder notes, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!notes.isEmpty()) {
            notes.append("; ");
        }
        notes.append(part.trim());
    }

    static String canonicalizeChineseUnit(String token) {
        if (token == null) {
            return "";
        }
        for (Map.Entry<String, String> entry : CHINESE_UNITS) {
            if (entry.getKey().equals(token)) {
                return entry.getValue();
            }
        }
        return token;
    }

    static String canonicalizeEnglishUnit(String token) {
        if (token == null) {
            return "";
        }
        String lower = token.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : ENGLISH_UNITS) {
            if (entry.getKey().equals(lower)) {
                return entry.getValue();
            }
        }
        return lower;
    }
}
