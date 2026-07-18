package com.cookcopilot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.langchain4j.model.openai.OpenAiChatModel;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RecipeImportServiceTest {

    @Mock
    private OpenAiChatModel chatModel;

    @InjectMocks
    private RecipeImportService recipeImportService;

    @Test
    @SuppressWarnings("unchecked")
    void tryParseRecipeFromJsonLd_readsSchemaOrgRecipe() throws Exception {
        String html = """
                <html><head>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "Recipe",
                  "name": "番茄炒蛋",
                  "description": "經典家常菜",
                  "recipeIngredient": ["蛋 3 顆", "番茄 2 顆"],
                  "recipeInstructions": [
                    {"@type": "HowToStep", "text": "打蛋入碗"},
                    {"@type": "HowToStep", "text": "下鍋炒熟"}
                  ]
                }
                </script>
                </head><body><p>noise</p></body></html>
                """;

        Method method = RecipeImportService.class.getDeclaredMethod(
                "tryParseRecipeFromJsonLd", String.class, String.class);
        method.setAccessible(true);
        Optional<Map<String, Object>> result =
                (Optional<Map<String, Object>>) method.invoke(recipeImportService, html, "https://icook.tw/recipes/1");

        assertTrue(result.isPresent());
        assertEquals("番茄炒蛋", result.get().get("name"));
        assertEquals("經典家常菜", result.get().get("description"));
        List<Map<String, String>> ingredients = (List<Map<String, String>>) result.get().get("ingredients");
        assertEquals(2, ingredients.size());
        assertEquals("蛋", ingredients.get(0).get("name"));
        assertEquals("3", ingredients.get(0).get("quantity"));
        assertEquals("pcs", ingredients.get(0).get("unit"));
        assertEquals("番茄", ingredients.get(1).get("name"));
        assertEquals("2", ingredients.get(1).get("quantity"));
        assertEquals("pcs", ingredients.get(1).get("unit"));
        List<String> steps = (List<String>) result.get().get("steps");
        assertEquals(List.of("打蛋入碗", "下鍋炒熟"), steps);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryParseRecipeFromJsonLd_parsesChineseCookingUnits() throws Exception {
        String html = """
                <html><head>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "Recipe",
                  "name": "茄子蕃茄雞胸肉",
                  "recipeIngredient": [
                    "[a-2 調味料] 黑醋 4大匙",
                    "[a-2 調味料] 砂糖(妊娠糖尿可不可) 2小匙",
                    "雞胸肉 1塊",
                    "[a-2 調味料] 蒜末 約2瓣",
                    "[a-1 調味料] 鹽巴(醃雞肉用) 少許"
                  ],
                  "recipeInstructions": ["炒熟"]
                }
                </script>
                </head><body></body></html>
                """;

        Method method = RecipeImportService.class.getDeclaredMethod(
                "tryParseRecipeFromJsonLd", String.class, String.class);
        method.setAccessible(true);
        Optional<Map<String, Object>> result =
                (Optional<Map<String, Object>>) method.invoke(recipeImportService, html, "https://icook.tw/recipes/2");

        assertTrue(result.isPresent());
        List<Map<String, String>> ingredients = (List<Map<String, String>>) result.get().get("ingredients");
        assertEquals(5, ingredients.size());

        assertEquals("黑醋", ingredients.get(0).get("name"));
        assertEquals("4", ingredients.get(0).get("quantity"));
        assertEquals("tbsp", ingredients.get(0).get("unit"));

        assertEquals("砂糖(妊娠糖尿可不可)", ingredients.get(1).get("name"));
        assertEquals("2", ingredients.get(1).get("quantity"));
        assertEquals("tsp", ingredients.get(1).get("unit"));

        assertEquals("雞胸肉", ingredients.get(2).get("name"));
        assertEquals("1", ingredients.get(2).get("quantity"));
        assertEquals("pcs", ingredients.get(2).get("unit"));

        assertEquals("蒜末", ingredients.get(3).get("name"));
        assertEquals("2", ingredients.get(3).get("quantity"));
        assertEquals("clove", ingredients.get(3).get("unit"));

        assertEquals("鹽巴(醃雞肉用)", ingredients.get(4).get("name"));
        assertEquals("", ingredients.get(4).get("quantity"));
        assertEquals("", ingredients.get(4).get("unit"));
        assertTrue(ingredients.get(4).get("note").contains("少許"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryParseRecipeFromJsonLd_readsRecipeInsideGraph() throws Exception {
        String html = """
                <html><head>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@graph": [
                    {"@type": "WebPage", "name": "Page"},
                    {
                      "@type": "Recipe",
                      "name": "Graph Recipe",
                      "recipeIngredient": ["flour"],
                      "recipeInstructions": "Mix well"
                    }
                  ]
                }
                </script>
                </head><body></body></html>
                """;

        Method method = RecipeImportService.class.getDeclaredMethod(
                "tryParseRecipeFromJsonLd", String.class, String.class);
        method.setAccessible(true);
        Optional<Map<String, Object>> result =
                (Optional<Map<String, Object>>) method.invoke(recipeImportService, html, "https://example.com/r");

        assertTrue(result.isPresent());
        assertEquals("Graph Recipe", result.get().get("name"));
        List<String> steps = (List<String>) result.get().get("steps");
        assertEquals(List.of("Mix well"), steps);
    }
}
