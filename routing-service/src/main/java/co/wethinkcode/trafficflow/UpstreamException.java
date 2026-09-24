package co.wethinkcode.trafficflow;

/**
 * A dependency routing-service needs could not be reached or answered with something
 * unusable. Surfaced to the caller as a {@code 502}, since the fault is behind us, not
 * in their request.
 */
public class UpstreamException extends RuntimeException {

    private final String upstream;

    public UpstreamException(String upstream, String message, Throwable cause) {
        super(message, cause);
        this.upstream = upstream;
    }

    public UpstreamException(String upstream, String message) {
        this(upstream, message, null);
    }

    public String upstream() {
        return upstream;
    }
}