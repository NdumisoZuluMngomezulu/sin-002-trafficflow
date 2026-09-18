package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.CongestionPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Tracks the city-wide congestion level (0-8) that routing-service factors into its
 * travel-time estimates.
 *
 * <p>Stage 2: routing-service polls {@code GET /congestion} per request.
 * Stage 3: every change is also published to the {@code congestion-topic} MQ topic, so
 * routing-service can stop polling — see {@code CongestionPublisher}.
 */
public class CongestionServiceApp {

    private static final Logger LOG = LoggerFactory.getLogger(CongestionServiceApp.class);
    private static final int DEFAULT_PORT = 7022;
    private static final int DEFAULT_LEVEL = 3;

    private final CongestionTracker tracker;
    private final CongestionPublisher publisher;
    private Javalin app;

    public CongestionServiceApp(CongestionTracker tracker, CongestionPublisher publisher) {
        this.tracker = tracker;
        this.publisher = publisher;
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT)));
        int initialLevel = Integer.parseInt(
                System.getenv().getOrDefault("CONGESTION_LEVEL", String.valueOf(DEFAULT_LEVEL)));
        boolean mqEnabled = !"false".equalsIgnoreCase(System.getenv().getOrDefault("MQ_ENABLED", "true"));
        CongestionPublisher publisher = mqEnabled ? new CongestionPublisher() : null;
        CongestionServiceApp service = new CongestionServiceApp(new CongestionTracker(initialLevel), publisher);
        Runtime.getRuntime().addShutdownHook(new Thread(service::stop));
        service.start(port);
    }

    public int start(int port) {
        if (publisher != null) {
            // The tracker doesn't know MQ exists; it just announces changes.
            tracker.onChange(publisher::publish);
        }

        app = Javalin.create(config -> config.showJavalinBanner = false);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/congestion", ctx -> ctx.json(tracker.current()));
        app.put("/congestion", this::setLevel);
        app.post("/congestion/increase", ctx -> ctx.json(tracker.adjust(1)));
        app.post("/congestion/decrease", ctx -> ctx.json(tracker.adjust(-1)));

        // A bad level is the caller's mistake, not ours — 400, with the range in the body.
        app.exception(IllegalArgumentException.class, (e, ctx) ->
                ctx.status(400).json(Map.of(
                        "error", e.getMessage(),
                        "min", CongestionTracker.MIN_LEVEL,
                        "max", CongestionTracker.MAX_LEVEL)));

        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("unhandled error on {} {}", ctx.method(), ctx.path(), e);
            ctx.status(500).json(Map.of("error", "internal error", "detail", String.valueOf(e.getMessage())));
        });

        app.start(port);
        LOG.info("congestion level starts at {} ({})", tracker.current().level(), tracker.current().label());
        return app.port();
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
        if (publisher != null) {
            publisher.close();
        }
    }

    private void setLevel(Context ctx) {
        if (ctx.body().isBlank()) {
            throw new IllegalArgumentException("body must be {\"level\": <integer 0-8>}");
        }
        JsonNode level = ctx.bodyAsClass(JsonNode.class).get("level");
        if (level == null || !level.canConvertToInt()) {
            throw new IllegalArgumentException("body must be {\"level\": <integer 0-8>}");
        }
        ctx.json(tracker.set(level.asInt()));
    }
}
