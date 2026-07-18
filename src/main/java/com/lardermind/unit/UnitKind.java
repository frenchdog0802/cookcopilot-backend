package com.lardermind.unit;

public enum UnitKind {
    WEIGHT,
    VOLUME,
    COUNT;

    public static UnitKind fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UnitKind.valueOf(value.trim().toUpperCase());
    }

    public String toApiValue() {
        return name().toLowerCase();
    }
}
