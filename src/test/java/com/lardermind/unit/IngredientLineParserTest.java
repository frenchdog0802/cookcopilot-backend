package com.lardermind.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngredientLineParserTest {

    @Test
    void parsesChineseQuantityAfterName() {
        var parsed = IngredientLineParser.parse("黑醋 4大匙");
        assertEquals("黑醋", parsed.name());
        assertEquals("4", parsed.quantity());
        assertEquals("tbsp", parsed.unit());
    }

    @Test
    void parsesChineseWithBracketCategoryAndApprox() {
        var parsed = IngredientLineParser.parse("[a-2 調味料] 蒜末 約2瓣");
        assertEquals("蒜末", parsed.name());
        assertEquals("2", parsed.quantity());
        assertEquals("clove", parsed.unit());
        assertEquals("a-2 調味料", parsed.note());
    }

    @Test
    void parsesChineseCountUnits() {
        assertEquals("pcs", IngredientLineParser.parse("茄子 2條").unit());
        assertEquals("2", IngredientLineParser.parse("茄子 2條").quantity());
        assertEquals("pcs", IngredientLineParser.parse("牛蕃茄 1顆").unit());
        assertEquals("pcs", IngredientLineParser.parse("雞胸肉 1塊").unit());
        assertEquals("tsp", IngredientLineParser.parse("砂糖(妊娠糖尿可不可) 2小匙").unit());
        assertEquals("砂糖(妊娠糖尿可不可)", IngredientLineParser.parse("砂糖(妊娠糖尿可不可) 2小匙").name());
    }

    @Test
    void parsesToTasteAsNoteWithoutFakePcs() {
        var parsed = IngredientLineParser.parse("[a-1 調味料] 鹽巴(醃雞肉用) 少許");
        assertEquals("鹽巴(醃雞肉用)", parsed.name());
        assertEquals("", parsed.quantity());
        assertEquals("", parsed.unit());
        assertEquals("a-1 調味料; 少許", parsed.note());
    }

    @Test
    void parsesSpacedChineseUnit() {
        var parsed = IngredientLineParser.parse("蛋 3 顆");
        assertEquals("蛋", parsed.name());
        assertEquals("3", parsed.quantity());
        assertEquals("pcs", parsed.unit());
    }

    @Test
    void parsesEnglishLeadingQuantity() {
        var parsed = IngredientLineParser.parse("2 cups flour");
        assertEquals("flour", parsed.name());
        assertEquals("2", parsed.quantity());
        assertEquals("cup", parsed.unit());
    }

    @Test
    void parsesFractionQuantity() {
        var parsed = IngredientLineParser.parse("米 1/2杯");
        assertEquals("米", parsed.name());
        assertEquals("0.5", parsed.quantity());
        assertEquals("cup", parsed.unit());
    }
}
