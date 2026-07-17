package com.cookplanner.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeImportService {

    private static final int MAX_HTML_BYTES = 512_000;
    private static final int MAX_TEXT_CHARS = 12_000;

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> extractRecipeFromUrl(String url) {
        validateUrl(url);
        String html = fetchHtml(url);
        String pageText = extractReadableText(html);
        return structureRecipeWithLlm(pageText, url);
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is required.");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid URL format.");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only http and https URLs are supported.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must include a host.");
        }
        if (isBlockedHost(host)) {
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
                    .header("User-Agent", "CookPlanner/1.0 RecipeImporter")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
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
            throw new IllegalArgumentException("Could not fetch URL: " + ex.getMessage());
        }
    }

    private String extractReadableText(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style, nav, footer, header, aside, noscript").remove();

        String jsonLd = doc.select("script[type=application/ld+json]").text();
        StringBuilder text = new StringBuilder();
        if (!jsonLd.isBlank()) {
            text.append("Structured data:\n").append(jsonLd).append("\n\n");
        }
        text.append(doc.text());
        if (text.length() > MAX_TEXT_CHARS) {
            return text.substring(0, MAX_TEXT_CHARS);
        }
        return text.toString();
    }

    private Map<String, Object> structureRecipeWithLlm(String pageText, String sourceUrl) {
        String prompt = """
                Extract a recipe from the following web page text. Return ONLY valid JSON with this shape:
                {
                  "name": "Recipe name",
                  "description": "Short description",
                  "ingredients": [{"name": "ingredient", "quantity": "1", "unit": "cup", "note": ""}],
                  "steps": ["Step 1", "Step 2"]
                }
                Source URL: %s
                Page text:
                %s
                """.formatted(sourceUrl, pageText);

        String response = chatModel.chat(prompt);
        String json = extractJsonObject(response);
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            if (!parsed.containsKey("name") || parsed.get("name") == null) {
                throw new IllegalArgumentException("Could not extract recipe name from page.");
            }
            return parsed;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not parse recipe from page content.");
        }
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
                .map(item -> new CookingTools.IngredientInput(
                        stringValue(item.get("name")),
                        stringValue(item.get("quantity")),
                        stringValue(item.get("unit")),
                        stringValue(item.get("note"))))
                .filter(input -> input.name() != null && !input.name().isBlank())
                .toList();
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
