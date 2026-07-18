package com.lardermind.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds a related stock photo for a recipe name and stores it on Cloudinary when possible.
 * Prefers Unsplash when {@code app.unsplash.access-key} is set; otherwise Openverse.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockImageService {

    private final CloudinaryService cloudinaryService;
    private final RestClient restClient = RestClient.create();

    @Value("${app.unsplash.access-key:}")
    private String unsplashAccessKey;

    /**
     * Returns {@code {url, public_id}} for a stock image matching the meal name,
     * or empty if nothing usable was found.
     */
    public Optional<Map<String, String>> resolveStockImage(String mealName) {
        Optional<String> remoteUrl = findImageUrl(mealName);
        if (remoteUrl.isEmpty()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> uploaded = cloudinaryService.uploadImageFromUrl(remoteUrl.get());
            String url = (String) uploaded.get("secure_url");
            String publicId = (String) uploaded.get("public_id");
            if (url == null || url.isBlank()) {
                return Optional.of(imageMap(remoteUrl.get(), null));
            }
            return Optional.of(imageMap(url, publicId));
        } catch (Exception ex) {
            log.warn("Cloudinary upload failed for stock image; using remote URL. reason={}", ex.getMessage());
            return Optional.of(imageMap(remoteUrl.get(), null));
        }
    }

    public Optional<String> findImageUrl(String mealName) {
        for (String query : buildQueries(mealName)) {
            if (unsplashAccessKey != null && !unsplashAccessKey.isBlank()) {
                Optional<String> unsplash = searchUnsplash(query);
                if (unsplash.isPresent()) {
                    return unsplash;
                }
            }
            Optional<String> openverse = searchOpenverse(query);
            if (openverse.isPresent()) {
                return openverse;
            }
        }
        return Optional.empty();
    }

    private List<String> buildQueries(String mealName) {
        Set<String> queries = new LinkedHashSet<>();
        if (mealName != null && !mealName.isBlank()) {
            String trimmed = mealName.trim();
            queries.add(trimmed + " food");
            queries.add(trimmed);
            queries.add(trimmed + " dish meal");
        }
        // Fallbacks when the dish name is rare / non-English
        queries.add("homemade dinner plate food");
        queries.add("delicious cooked meal");
        return new ArrayList<>(queries);
    }

    private Optional<String> searchUnsplash(String query) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString("https://api.unsplash.com/search/photos")
                    .queryParam("query", query)
                    .queryParam("per_page", 1)
                    .queryParam("orientation", "landscape")
                    .build()
                    .encode()
                    .toUri();

            JsonNode root = restClient.get()
                    .uri(uri)
                    .header("Authorization", "Client-ID " + unsplashAccessKey)
                    .header("Accept-Version", "v1")
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null) {
                return Optional.empty();
            }
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }
            String url = results.get(0).path("urls").path("regular").asText(null);
            return Optional.ofNullable(blankToNull(url));
        } catch (Exception ex) {
            log.warn("Unsplash search failed for query '{}': {}", query, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> searchOpenverse(String query) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString("https://api.openverse.org/v1/images/")
                    .queryParam("q", query)
                    .queryParam("page_size", 1)
                    .queryParam("format", "json")
                    .build()
                    .encode()
                    .toUri();

            JsonNode root = restClient.get()
                    .uri(uri)
                    .header("User-Agent", "LarderMind/1.0")
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null) {
                return Optional.empty();
            }
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = results.get(0);
            String url = first.path("url").asText(null);
            if (blankToNull(url) == null) {
                url = first.path("thumbnail").asText(null);
            }
            return Optional.ofNullable(blankToNull(url));
        } catch (Exception ex) {
            log.warn("Openverse search failed for query '{}': {}", query, ex.getMessage());
            return Optional.empty();
        }
    }

    private static Map<String, String> imageMap(String url, String publicId) {
        Map<String, String> image = new LinkedHashMap<>();
        image.put("url", url);
        if (publicId != null && !publicId.isBlank()) {
            image.put("public_id", publicId);
        }
        return image;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
