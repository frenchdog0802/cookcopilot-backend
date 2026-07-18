package com.cookcopilot.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnitConverterTest {

    @Test
    void convertsWeightAndVolume() {
        assertEquals(1000.0, UnitConverter.convert(1, "kg", "g"), 0.001);
        assertEquals(240.0, UnitConverter.convert(1, "cup", "ml"), 0.001);
        assertEquals(15.0, UnitConverter.convert(1, "tbsp", "ml"), 0.001);
    }

    @Test
    void clampsShortage() {
        assertEquals(0.0, UnitConverter.clampShortage(100, 150));
        assertEquals(50.0, UnitConverter.clampShortage(100, 50));
    }

    @Test
    void normalizesAliases() {
        assertEquals("g", UnitConverter.normalize("grams"));
        assertEquals("clove", UnitConverter.normalize("cloves"));
        assertEquals("tbsp", UnitConverter.normalize("大匙"));
        assertEquals("tsp", UnitConverter.normalize("小匙"));
        assertEquals("pcs", UnitConverter.normalize("顆"));
        assertEquals("clove", UnitConverter.normalize("瓣"));
    }

    @Test
    void rejectsCrossKindConversion() {
        assertThrows(RuntimeException.class, () -> UnitConverter.convert(1, "g", "ml"));
    }
}
