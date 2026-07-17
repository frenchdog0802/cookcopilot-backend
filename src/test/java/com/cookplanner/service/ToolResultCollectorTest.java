package com.cookplanner.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultCollectorTest {

    @Test
    void setResult_populatesFields() {
        ToolResultCollector collector = new ToolResultCollector();
        Map<String, Object> data = Map.of("recipeId", UUID.randomUUID());

        collector.setResult("createRecipe", data);

        assertTrue(collector.hasResult());
        assertEquals("createRecipe", collector.getToolName());
        assertEquals(data, collector.getData());
    }

    @Test
    void clear_resetsState() {
        ToolResultCollector collector = new ToolResultCollector();
        collector.setResult("createRecipe", Map.of("recipeName", "Pasta"));

        collector.clear();

        assertFalse(collector.hasResult());
        assertNull(collector.getToolName());
        assertNull(collector.getData());
    }

    @Test
    void lastWriteWinsForSingleResultAccessors() {
        ToolResultCollector collector = new ToolResultCollector();
        collector.setResult("createRecipe", Map.of("recipeName", "Pasta"));
        collector.addResult("addItemsToShoppingList", Map.of("itemsAdded", 2));

        assertEquals("addItemsToShoppingList", collector.getToolName());
        assertEquals(2, collector.getData().get("itemsAdded"));
        assertEquals(2, collector.getResults().size());
        assertEquals("multi_action", collector.primaryResponseType());
    }
}
