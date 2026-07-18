package com.lardermind.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import com.lardermind.unit.IngredientLineParser;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeImportService {

    private static final int MAX_HTML_BYTES = 512_000;
    private static final int MAX_TEXT_CHARS = 12_000;

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> extractRecipeFromUrl(String url) {
        String safeUrl = sanitizeUrlForLog(url);
        log.info("Recipe import extract started url={}", safeUrl);
        validateUrl(url);
        String html = fetchHtml(url);

        Optional<Map<String, Object>> fromJsonLd = tryParseRecipeFromJsonLd(html, safeUrl);
        if (fromJsonLd.isPresent()) {
            log.info("Recipe import used JSON-LD url={} name={}", safeUrl, fromJsonLd.get().get("name"));
            return fromJsonLd.get();
        }

        String pageText = extractReadableText(html);
        log.info("Recipe import page fetched url={} htmlBytes≈{} textChars={}",
                safeUrl, html.length(), pageText.length());
        if (pageText.isBlank() || pageText.length() < 40) {
            log.warn("Recipe import page text too short url={} textChars={}", safeUrl, pageText.length());
            throw new IllegalArgumentException("Page did not contain enough readable recipe content.");
        }

        Map<String, Object> recipe = structureRecipeWithLlm(pageText, url);
        log.info("Recipe import structured url={} name={}", safeUrl, recipe.get("name"));
        return recipe;
    }

    /** Host + path only — strips query/fragment so tokens are not logged. */
    public static String sanitizeUrlForLog(String url) {
        if (url == null || url.isBlank()) {
            return "(blank)";
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme() + "://";
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host.isBlank() && path.isBlank()) {
                return url.trim().length() > 120 ? url.trim().substring(0, 120) + "…" : url.trim();
            }
            return scheme + host + path;
        } catch (Exception ex) {
            String trimmed = url.trim();
            return trimmed.length() > 120 ? trimmed.substring(0, 120) + "…" : trimmed;
        }
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            log.warn("Recipe import rejected: URL is blank");
            throw new IllegalArgumentException("URL is required.");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("Recipe import rejected: invalid URL format url={}", sanitizeUrlForLog(url));
            throw new IllegalArgumentException("Invalid URL format.");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            log.warn("Recipe import rejected: unsupported scheme url={}", sanitizeUrlForLog(url));
            throw new IllegalArgumentException("Only http and https URLs are supported.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            log.warn("Recipe import rejected: missing host url={}", sanitizeUrlForLog(url));
            throw new IllegalArgumentException("URL must include a host.");
        }
        if (isBlockedHost(host)) {
            log.warn("Recipe import rejected: blocked host host={} url={}", host, sanitizeUrlForLog(url));
            throw new IllegalArgumentException("URL host is not allowed.");
        }
    }

    private boolean isBlockedHost(String host) {
        String lower = host.toLowerCase();
        if (lower.equals("localhost") || lower.endsWith(".local")) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()) {
                return true;
            }
            byte[] bytes = address.getAddress();
            if (bytes.length == 4) {
                int b0 = Byte.toUnsignedInt(bytes[0]);
                int b1 = Byte.toUnsignedInt(bytes[1]);
                if (b0 == 10) {
                    return true;
                }
                if (b0 == 172 && b1 >= 16 && b1 <= 31) {
                    return true;
                }
                if (b0 == 192 && b1 == 168) {
                    return true;
                }
                if (b0 == 127) {
                    return true;
                }
            }
        } catch (Exception ex) {
            log.warn("Could not resolve host {}: {}", host, ex.getMessage());
            return true;
        }
        return false;
    }

    private String fetchHtml(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Recipe import fetch failed url={} httpStatus={}", sanitizeUrlForLog(url), response.statusCode());
                throw new IllegalArgumentException("Failed to fetch URL (HTTP " + response.statusCode() + ").");
            }
            byte[] body = response.body();
            if (body.length > MAX_HTML_BYTES) {
                body = java.util.Arrays.copyOf(body, MAX_HTML_BYTES);
            }
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Recipe import fetch error url={} exception={}: {}",
                    sanitizeUrlForLog(url), ex.getClass().getSimpleName(), ex.getMessage());
            throw new IllegalArgumentException("Could not fetch URL: " + ex.getMessage());
        }
    }

    /**
     * Prefer schema.org Recipe in JSON-LD when present (common on recipe sites like iCook).
     * Must run before script tags are stripped from the document.
     */
    private Optional<Map<String, Object>> tryParseRecipeFromJsonLd(String html, String safeUrl) {
        Document doc = Jsoup.parse(html);
        Elements scripts = doc.select("script[type=application/ld+json]");
        if (scripts.isEmpty()) {
            log.info("Recipe import no JSON-LD scripts url={}", safeUrl);
            return Optional.empty();
        }
        for (Element script : scripts) {
            String raw = script.html();
            if (raw == null || raw.isBlank()) {
                raw = script.data();
            }
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(raw.trim());
                Optional<JsonNode> recipeNode = findRecipeNode(root);
                if (recipeNode.isEmpty()) {
                    continue;
                }
                Optional<Map<String, Object>> mapped = mapSchemaOrgRecipe(recipeNode.get());
                if (mapped.isPresent()) {
                    return mapped;
                }
            } catch (Exception ex) {
                log.warn("Recipe import JSON-LD parse skipped url={} exception={}: {}",
                        safeUrl, ex.getClass().getSimpleName(), ex.getMessage());
            }
        }
        log.info("Recipe import JSON-LD present but no usable Recipe url={}", safeUrl);
        return Optional.empty();
    }

    private Optional<JsonNode> findRecipeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<JsonNode> found = findRecipeNode(child);
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
        }
        if (node.isObject()) {
            if (isRecipeType(node.get("@type"))) {
                return Optional.of(node);
            }
            JsonNode graph = node.get("@graph");
            if (graph != null) {
                return findRecipeNode(graph);
            }
            // Some pages nest Recipe under mainEntity
            JsonNode mainEntity = node.get("mainEntity");
            if (mainEntity != null) {
                Optional<JsonNode> found = findRecipeNode(mainEntity);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isRecipeType(JsonNode typeNode) {
        if (typeNode == null || typeNode.isNull()) {
            return false;
        }
        if (typeNode.isTextual()) {
            return typeNode.asText().toLowerCase().contains("recipe");
        }
        if (typeNode.isArray()) {
            for (JsonNode t : typeNode) {
                if (t.isTextual() && t.asText().toLowerCase().contains("recipe")) {
                    return true;
                }
            }
        }
        return false;
    }

    private Optional<Map<String, Object>> mapSchemaOrgRecipe(JsonNode recipe) {
        String name = textOrNull(recipe.get("name"));
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name.trim());
        String description = textOrNull(recipe.get("description"));
        result.put("description", description == null ? "" : description.trim());
        result.put("ingredients", mapIngredients(recipe.get("recipeIngredient")));
        result.put("steps", mapInstructions(recipe.get("recipeInstructions")));
        return Optional.of(result);
    }

    private List<Map<String, String>> mapIngredients(JsonNode ingredientsNode) {
        List<Map<String, String>> ingredients = new ArrayList<>();
        if (ingredientsNode == null || ingredientsNode.isNull()) {
            return ingredients;
        }
        if (ingredientsNode.isArray()) {
            for (JsonNode item : ingredientsNode) {
                String line = textOrNull(item);
                if (line == null || line.isBlank()) {
                    continue;
                }
                ingredients.add(IngredientLineParser.toMap(line.trim()));
            }
        } else if (ingredientsNode.isTextual()) {
            String line = ingredientsNode.asText().trim();
            if (!line.isBlank()) {
                ingredients.add(IngredientLineParser.toMap(line));
            }
        }
        return ingredients;
    }

    private List<String> mapInstructions(JsonNode instructionsNode) {
        List<String> steps = new ArrayList<>();
        if (instructionsNode == null || instructionsNode.isNull()) {
            return steps;
        }
        if (instructionsNode.isTextual()) {
            String text = instructionsNode.asText().trim();
            if (!text.isBlank()) {
                for (String part : text.split("\\n+")) {
                    if (!part.isBlank()) {
                        steps.add(part.trim());
                    }
                }
            }
            return steps;
        }
        if (instructionsNode.isArray()) {
            for (JsonNode item : instructionsNode) {
                if (item.isTextual()) {
                    String step = item.asText().trim();
                    if (!step.isBlank()) {
                        steps.add(step);
                    }
                    continue;
                }
                if (item.isObject()) {
                    String step = textOrNull(item.get("text"));
                    if (step == null || step.isBlank()) {
                        step = textOrNull(item.get("name"));
                    }
                    if (step != null && !step.isBlank()) {
                        steps.add(step.trim());
                    }
                    JsonNode itemList = item.get("itemListElement");
                    if (itemList != null) {
                        steps.addAll(mapInstructions(itemList));
                    }
                }
            }
        } else if (instructionsNode.isObject()) {
            JsonNode itemList = instructionsNode.get("itemListElement");
            if (itemList != null) {
                return mapInstructions(itemList);
            }
            String step = textOrNull(instructionsNode.get("text"));
            if (step != null && !step.isBlank()) {
                steps.add(step.trim());
            }
        }
        return steps;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return null;
    }

    private String extractReadableText(String html) {
        Document doc = Jsoup.parse(html);

        // Collect JSON-LD before removing scripts (previous bug discarded it).
        StringBuilder text = new StringBuilder();
        Elements jsonLdScripts = doc.select("script[type=application/ld+json]");
        for (Element script : jsonLdScripts) {
            String raw = script.html();
            if (raw == null || raw.isBlank()) {
                raw = script.data();
            }
            if (raw != null && !raw.isBlank()) {
                text.append("Structured data:\n").append(raw.trim()).append("\n\n");
            }
        }

        doc.select("script, style, noscript, svg, iframe").remove();

        Element main = firstPresent(doc, "article", "main", "[itemtype*=Recipe]", ".recipe", "#recipe");
        String bodyText = main != null ? main.text() : doc.body() != null ? doc.body().text() : doc.text();
        text.append(bodyText);

        if (text.length() > MAX_TEXT_CHARS) {
            return text.substring(0, MAX_TEXT_CHARS);
        }
        return text.toString();
    }

    private static Element firstPresent(Document doc, String... cssQueries) {
        for (String query : cssQueries) {
            Element el = doc.selectFirst(query);
            if (el != null) {
                return el;
            }
        }
        return null;
    }

    private Map<String, Object> structureRecipeWithLlm(String pageText, String sourceUrl) {
        // Avoid String.formatted/pageText — page content may contain '%' and break formatting.
        String prompt = """
                Extract a recipe from the following web page text. Return ONLY valid JSON with this shape:
                {
                  "name": "Recipe name",
                  "description": "Short description",
                  "ingredients": [{"name": "ingredient", "quantity": "1", "unit": "cup", "note": ""}],
                  "steps": ["Step 1", "Step 2"]
                }
                If the page is not a recipe, still return the same JSON shape with your best effort from available content.
                Source URL:
                """
                + sourceUrl
                + "\nPage text:\n"
                + pageText;

        String response;
        try {
            response = chatModel.chat(prompt);
        } catch (Exception ex) {
            log.warn("Recipe import LLM call failed url={} exception={}: {}",
                    sanitizeUrlForLog(sourceUrl), ex.getClass().getSimpleName(), ex.getMessage());
            throw new IllegalArgumentException("Could not analyze recipe page: " + ex.getMessage());
        }

        if (response == null || response.isBlank()) {
            log.warn("Recipe import LLM returned empty response url={} responseLen=0",
                    sanitizeUrlForLog(sourceUrl));
            throw new IllegalArgumentException("Could not parse recipe from page content (empty model response).");
        }

        log.info("Recipe import LLM response url={} responseLen={} preview={}",
                sanitizeUrlForLog(sourceUrl), response.length(), previewForLog(response));

        String json = extractJsonObject(response);
        if (json == null || json.isBlank() || !json.trim().startsWith("{")) {
            log.warn("Recipe import LLM response had no JSON object url={} preview={}",
                    sanitizeUrlForLog(sourceUrl), previewForLog(response));
            throw new IllegalArgumentException("Could not parse recipe from page content (no JSON in model response).");
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            if (!parsed.containsKey("name") || parsed.get("name") == null
                    || parsed.get("name").toString().isBlank()) {
                log.warn("Recipe import LLM result missing name url={} jsonPreview={}",
                        sanitizeUrlForLog(sourceUrl), previewForLog(json));
                throw new IllegalArgumentException("Could not extract recipe name from page.");
            }
            return parsed;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Recipe import LLM JSON parse failed url={} exception={}: {} jsonPreview={}",
                    sanitizeUrlForLog(sourceUrl), ex.getClass().getSimpleName(), ex.getMessage(),
                    previewForLog(json));
            throw new IllegalArgumentException("Could not parse recipe from page content.");
        }
    }

    private static String previewForLog(String value) {
        if (value == null) {
            return "(null)";
        }
        String trimmed = value.replace('\n', ' ').trim();
        return trimmed.isEmpty() ? "(empty)" : (trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed);
    }

    private String extractJsonObject(String response) {
        if (response == null) {
            throw new IllegalArgumentException("Empty LLM response.");
        }
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    @SuppressWarnings("unchecked")
    public static List<CookingTools.IngredientInput> toIngredientInputs(Map<String, Object> extracted) {
        Object raw = extracted.get("ingredients");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .map(RecipeImportService::toIngredientInput)
                .filter(input -> input.name() != null && !input.name().isBlank())
                .toList();
    }

    private static CookingTools.IngredientInput toIngredientInput(Map<String, Object> item) {
        String name = stringValue(item.get("name"));
        String quantity = stringValue(item.get("quantity"));
        String unit = stringValue(item.get("unit"));
        String note = stringValue(item.get("note"));

        // JSON-LD / LLM sometimes leaves qty empty while the amount is still in the name.
        if ((quantity == null || quantity.isBlank()) && name != null && !name.isBlank()) {
            IngredientLineParser.ParsedIngredient parsed = IngredientLineParser.parse(name);
            if (parsed.quantity() != null && !parsed.quantity().isBlank()) {
                name = parsed.name();
                quantity = parsed.quantity();
                if (unit == null || unit.isBlank()) {
                    unit = parsed.unit();
                }
                note = mergeNotes(note, parsed.note());
            } else if ((unit == null || unit.isBlank()) && parsed.note() != null && !parsed.note().isBlank()) {
                // e.g. "鹽巴 少許" → name without to-taste word, note carries 少許
                name = parsed.name();
                note = mergeNotes(note, parsed.note());
            }
        }

        return new CookingTools.IngredientInput(name, quantity, unit, note);
    }

    private static String mergeNotes(String existing, String extra) {
        if (extra == null || extra.isBlank()) {
            return existing == null ? "" : existing;
        }
        if (existing == null || existing.isBlank()) {
            return extra;
        }
        if (existing.contains(extra)) {
            return existing;
        }
        return existing + "; " + extra;
    }

    @SuppressWarnings("unchecked")
    public static List<String> toSteps(Map<String, Object> extracted) {
        Object raw = extracted.get("steps");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(Object::toString).filter(s -> !s.isBlank()).toList();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
