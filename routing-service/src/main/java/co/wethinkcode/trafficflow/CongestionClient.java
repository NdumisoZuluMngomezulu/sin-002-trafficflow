package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Polls congestion-service for the current city-wide level.
 *
 * <p>Stage 2 calls this once per route request. Once the MQ topic is wired up
 * (stage 3) it becomes the fallback {@link CongestionProvider} uses when no message has
 * arrived yet.
 */
public class CongestionClient {

    public static final String SOURCE = "congestion-service (REST)";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public CongestionClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** @throws UpstreamException if congestion-service is unreachable or unusable */
    public CongestionReading read() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/congestion"))
                .timeout(Duration.ofSeconds(3))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new UpstreamException("congestion-service",
                        "congestion-service returned HTTP " + response.statusCode());
            }
            JsonNode level = mapper.readTree(response.body()).get("level");
            if (level == null || !level.canConvertToInt()) {
                throw new UpstreamException("congestion-service", "no usable congestion level in the response");
            }
            return new CongestionReading(level.asInt(), SOURCE, Instant.now().toString());
        } catch (IOException e) {
            throw new UpstreamException("congestion-service", "could not reach " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("congestion-service", "interrupted calling " + baseUrl, e);
        }
    }
}
