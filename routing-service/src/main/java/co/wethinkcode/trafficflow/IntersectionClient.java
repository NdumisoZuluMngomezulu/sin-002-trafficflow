package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Asks intersection-service whether a route endpoint is real.
 *
 * <p>Keeps "we looked and it isn't there" ({@link Optional#empty()}) apart from "we
 * couldn't look" ({@link UpstreamException}). Those are a 404 and a 502 respectively to
 * our own callers, and conflating them would hide an outage behind a plausible-looking
 * "unknown intersection".
 */
public class IntersectionClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public IntersectionClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Optional<Intersection> find(String id) {
        String url = baseUrl + "/intersections/" + URLEncoder.encode(id, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                throw new UpstreamException("intersection-service",
                        "intersection-service returned HTTP " + response.statusCode());
            }
            JsonNode node = mapper.readTree(response.body());
            return Optional.of(new Intersection(
                    text(node, "id"), text(node, "district"), text(node, "signalType"), bool(node, "active")));
        } catch (IOException e) {
            throw new UpstreamException("intersection-service", "could not reach " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("intersection-service", "interrupted calling " + baseUrl, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asBoolean();
    }
}
