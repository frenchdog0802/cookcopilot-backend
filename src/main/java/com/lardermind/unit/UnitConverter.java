package com.lardermind.unit;

import com.lardermind.common.GlobalExceptionHandler.BadRequestException;
import com.lardermind.entity.Ingredient;

import java.util.*;

/**
 * Canonical unit conversion for recipe / cart / pantry quantities.
 * Storage is always in the ingredient's base unit.
 */
public final class UnitConverter {

    public static final String G = "g";
    public static final String KG = "kg";
    public static final String OZ = "oz";
    public static final String LB = "lb";
    public static final String ML = "ml";
    public static final String L = "l";
    public static final String TSP = "tsp";
    public static final String TBSP = "tbsp";
    public static final String CUP = "cup";
    public static final String PCS = "pcs";
    public static final String CLOVE = "clove";
    public static final String CAN = "can";
    public static final String SLICE = "slice";
    public static final String STALK = "stalk";
    public static final String BUNCH = "bunch";

    private static final double OZ_TO_G = 28.3495;
    private static final double LB_TO_G = 453.592;
    private static final double TSP_TO_ML = 5.0;
    private static final double TBSP_TO_ML = 15.0;
    private static final double CUP_TO_ML = 240.0;

    private static final Set<String> WEIGHT_UNITS = Set.of(G, KG, OZ, LB);
    private static final Set<String> VOLUME_UNITS = Set.of(ML, L, TSP, TBSP, CUP);
    private static final Set<String> COUNT_UNITS = Set.of(PCS, CLOVE, CAN, SLICE, STALK, BUNCH);

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("gram", G), Map.entry("grams", G), Map.entry("gr", G),
            Map.entry("kilogram", KG), Map.entry("kilograms", KG), Map.entry("kgs", KG),
            Map.entry("ounce", OZ), Map.entry("ounces", OZ),
            Map.entry("pound", LB), Map.entry("pounds", LB), Map.entry("lbs", LB),
            Map.entry("milliliter", ML), Map.entry("milliliters", ML), Map.entry("millilitre", ML),
            Map.entry("millilitres", ML), Map.entry("mls", ML),
            Map.entry("liter", L), Map.entry("liters", L), Map.entry("litre", L), Map.entry("litres", L),
            Map.entry("teaspoon", TSP), Map.entry("teaspoons", TSP), Map.entry("tsps", TSP),
            Map.entry("tablespoon", TBSP), Map.entry("tablespoons", TBSP), Map.entry("tbsps", TBSP), Map.entry("tb", TBSP),
            Map.entry("cups", CUP),
            Map.entry("pc", PCS), Map.entry("piece", PCS), Map.entry("pieces", PCS), Map.entry("ea", PCS),
            Map.entry("each", PCS), Map.entry("cloves", CLOVE),
            Map.entry("cans", CAN), Map.entry("slices", SLICE), Map.entry("stalks", STALK), Map.entry("bunches", BUNCH),
            // Chinese cooking units (iCook / recipe blogs)
            Map.entry("大匙", TBSP), Map.entry("湯匙", TBSP),
            Map.entry("小匙", TSP), Map.entry("茶匙", TSP),
            Map.entry("杯", CUP),
            Map.entry("克", G), Map.entry("公克", G),
            Map.entry("公斤", KG),
            Map.entry("毫升", ML),
            Map.entry("升", L), Map.entry("公升", L),
            Map.entry("顆", PCS), Map.entry("個", PCS), Map.entry("塊", PCS), Map.entry("條", PCS), Map.entry("根", PCS),
            Map.entry("片", SLICE),
            Map.entry("瓣", CLOVE),
            Map.entry("罐", CAN),
            Map.entry("把", BUNCH), Map.entry("束", BUNCH)
    );

    private UnitConverter() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (ALIASES.containsKey(key)) {
            return ALIASES.get(key);
        }
        if (WEIGHT_UNITS.contains(key) || VOLUME_UNITS.contains(key) || COUNT_UNITS.contains(key)) {
            return key;
        }
        return key;
    }

    public static UnitKind kindOf(String unit) {
        String n = normalize(unit);
        if (n == null) return null;
        if (WEIGHT_UNITS.contains(n)) return UnitKind.WEIGHT;
        if (VOLUME_UNITS.contains(n)) return UnitKind.VOLUME;
        if (COUNT_UNITS.contains(n)) return UnitKind.COUNT;
        return null;
    }

    public static String baseUnitForKind(UnitKind kind) {
        return switch (kind) {
            case WEIGHT -> G;
            case VOLUME -> ML;
            case COUNT -> PCS;
        };
    }

    public static String baseUnitForKind(UnitKind kind, String preferredCountUnit) {
        if (kind == UnitKind.COUNT) {
            String n = normalize(preferredCountUnit);
            if (n != null && COUNT_UNITS.contains(n)) {
                return n;
            }
            return PCS;
        }
        return baseUnitForKind(kind);
    }

    public static List<String> allowedUnits(UnitKind kind) {
        return switch (kind) {
            case WEIGHT -> List.of(G, KG, OZ, LB);
            case VOLUME -> List.of(ML, L, TSP, TBSP, CUP);
            case COUNT -> List.of(PCS, CLOVE, CAN, SLICE, STALK, BUNCH);
        };
    }

    public static List<String> displayUnitsForPreference(UnitKind kind, String measurementSystem) {
        boolean imperial = measurementSystem != null && measurementSystem.toLowerCase(Locale.ROOT).startsWith("imp");
        return switch (kind) {
            case WEIGHT -> imperial ? List.of(OZ, LB) : List.of(G, KG);
            case VOLUME -> imperial ? List.of(TSP, TBSP, CUP) : List.of(ML, L);
            case COUNT -> List.of(PCS, CLOVE, CAN, SLICE, STALK, BUNCH);
        };
    }

    /** Convert quantity from one unit to another within the same kind. */
    public static double convert(double quantity, String fromUnit, String toUnit) {
        String from = normalize(fromUnit);
        String to = normalize(toUnit);
        if (from == null || to == null) {
            throw new BadRequestException("Unknown unit");
        }
        if (from.equals(to)) {
            return quantity;
        }
        UnitKind fromKind = kindOf(from);
        UnitKind toKind = kindOf(to);
        if (fromKind == null || toKind == null || fromKind != toKind) {
            throw new BadRequestException("Cannot convert from " + from + " to " + to);
        }
        if (fromKind == UnitKind.COUNT) {
            throw new BadRequestException("Count units cannot be converted (" + from + " → " + to + ")");
        }
        double base = toBase(quantity, from, fromKind);
        return fromBase(base, to, fromKind);
    }

    public static double toBase(double quantity, String unit, UnitKind kind) {
        String n = normalize(unit);
        if (n == null) {
            throw new BadRequestException("Missing unit");
        }
        return switch (kind) {
            case WEIGHT -> switch (n) {
                case G -> quantity;
                case KG -> quantity * 1000.0;
                case OZ -> quantity * OZ_TO_G;
                case LB -> quantity * LB_TO_G;
                default -> throw new BadRequestException("Invalid weight unit: " + n);
            };
            case VOLUME -> switch (n) {
                case ML -> quantity;
                case L -> quantity * 1000.0;
                case TSP -> quantity * TSP_TO_ML;
                case TBSP -> quantity * TBSP_TO_ML;
                case CUP -> quantity * CUP_TO_ML;
                default -> throw new BadRequestException("Invalid volume unit: " + n);
            };
            case COUNT -> {
                if (!COUNT_UNITS.contains(n)) {
                    throw new BadRequestException("Invalid count unit: " + n);
                }
                yield quantity;
            }
        };
    }

    public static double fromBase(double baseQuantity, String unit, UnitKind kind) {
        String n = normalize(unit);
        if (n == null) {
            throw new BadRequestException("Missing unit");
        }
        return switch (kind) {
            case WEIGHT -> switch (n) {
                case G -> baseQuantity;
                case KG -> baseQuantity / 1000.0;
                case OZ -> baseQuantity / OZ_TO_G;
                case LB -> baseQuantity / LB_TO_G;
                default -> throw new BadRequestException("Invalid weight unit: " + n);
            };
            case VOLUME -> switch (n) {
                case ML -> baseQuantity;
                case L -> baseQuantity / 1000.0;
                case TSP -> baseQuantity / TSP_TO_ML;
                case TBSP -> baseQuantity / TBSP_TO_ML;
                case CUP -> baseQuantity / CUP_TO_ML;
                default -> throw new BadRequestException("Invalid volume unit: " + n);
            };
            case COUNT -> baseQuantity;
        };
    }

    public static void ensureCompatible(Ingredient ingredient, String unit) {
        UnitKind kind = resolveKind(ingredient);
        String n = normalize(unit);
        if (n == null) {
            throw new BadRequestException("Unit is required");
        }
        UnitKind unitKind = kindOf(n);
        if (unitKind == null) {
            throw new BadRequestException("Unknown unit: " + unit);
        }
        if (kind != null && unitKind != kind) {
            throw new BadRequestException("Unit " + n + " does not match ingredient kind " + kind.toApiValue());
        }
        if (kind == UnitKind.COUNT) {
            String base = resolveBaseUnit(ingredient);
            if (!n.equals(base)) {
                throw new BadRequestException("Count ingredient must use unit " + base);
            }
        }
    }

    public static UnitKind resolveKind(Ingredient ingredient) {
        if (ingredient.getUnitKind() != null && !ingredient.getUnitKind().isBlank()) {
            return UnitKind.fromString(ingredient.getUnitKind());
        }
        String base = resolveBaseUnit(ingredient);
        UnitKind inferred = kindOf(base);
        return inferred != null ? inferred : UnitKind.COUNT;
    }

    public static String resolveBaseUnit(Ingredient ingredient) {
        String base = normalize(ingredient.getBaseUnit());
        if (base != null) {
            return base;
        }
        String legacy = normalize(ingredient.getDefaultUnit());
        if (legacy != null) {
            UnitKind k = kindOf(legacy);
            if (k == UnitKind.WEIGHT) return G;
            if (k == UnitKind.VOLUME) return ML;
            if (k == UnitKind.COUNT) return legacy;
            return legacy;
        }
        return PCS;
    }

    public static String resolveDisplayUnit(Ingredient ingredient) {
        String display = normalize(ingredient.getDefaultDisplayUnit());
        if (display != null) {
            return display;
        }
        return resolveBaseUnit(ingredient);
    }

    /**
     * Convert an incoming quantity+unit into the ingredient's base quantity.
     * If unit is blank, assumes quantity is already in base units.
     */
    public static double toIngredientBase(Ingredient ingredient, double quantity, String unit) {
        UnitKind kind = resolveKind(ingredient);
        String base = resolveBaseUnit(ingredient);
        if (unit == null || unit.isBlank()) {
            return quantity;
        }
        ensureCompatible(ingredient, unit);
        String n = normalize(unit);
        if (kind == UnitKind.COUNT) {
            return quantity;
        }
        return convert(quantity, n, base);
    }

    public static void applyDefaults(Ingredient ingredient) {
        UnitKind kind = resolveKind(ingredient);
        if (ingredient.getUnitKind() == null || ingredient.getUnitKind().isBlank()) {
            ingredient.setUnitKind(kind.toApiValue());
        } else {
            kind = UnitKind.fromString(ingredient.getUnitKind());
            ingredient.setUnitKind(kind.toApiValue());
        }

        String base = normalize(ingredient.getBaseUnit());
        if (base == null) {
            base = baseUnitForKind(kind, ingredient.getDefaultUnit());
        } else if (kind != UnitKind.COUNT) {
            base = baseUnitForKind(kind);
        } else if (!COUNT_UNITS.contains(base)) {
            base = PCS;
        }
        ingredient.setBaseUnit(base);
        ingredient.setDefaultUnit(base);

        String display = normalize(ingredient.getDefaultDisplayUnit());
        if (display == null) {
            display = base;
        } else {
            UnitKind displayKind = kindOf(display);
            if (displayKind != kind) {
                display = base;
            }
            if (kind == UnitKind.COUNT && !display.equals(base)) {
                display = base;
            }
        }
        ingredient.setDefaultDisplayUnit(display);
    }

    public static Ingredient inferAndBuild(String name, String unitHint) {
        String normalized = normalize(unitHint);
        UnitKind kind = kindOf(normalized);
        if (kind == null) {
            kind = UnitKind.COUNT;
            normalized = PCS;
        }
        String base = kind == UnitKind.COUNT ? normalized : baseUnitForKind(kind);
        return Ingredient.builder()
                .name(name)
                .unitKind(kind.toApiValue())
                .baseUnit(base)
                .defaultUnit(base)
                .defaultDisplayUnit(normalized != null ? normalized : base)
                .build();
    }

    /**
     * Convert to ingredient base; if unit is unknown/incompatible, treat quantity as already base.
     */
    public static double toIngredientBaseLenient(Ingredient ingredient, double quantity, String unit) {
        try {
            return toIngredientBase(ingredient, quantity, unit);
        } catch (RuntimeException ex) {
            return quantity;
        }
    }

    public static double clampShortage(double neededBase, double availableBase) {
        return Math.max(0.0, neededBase - availableBase);
    }
}
