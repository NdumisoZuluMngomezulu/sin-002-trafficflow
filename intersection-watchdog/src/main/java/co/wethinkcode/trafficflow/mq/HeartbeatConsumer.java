package co.wethinkcode.trafficflow.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * Listens for intersection-service's heartbeats, and for any that the broker gave up on.
 *
 * <p>Two subscriptions, because there are two ways to learn the service is in trouble:
 *
 * <ul>
 *   <li>the heartbeat queue goes quiet — noticed by {@link WatchdogState#evaluate}, not here;</li>
 *   <li>a heartbeat lands on the dead-letter queue, which the broker does when one
 *       expires unconsumed. That's a message we can actually see, so it raises an alert
 *       immediately rather than waiting for the timeout.</li>
 * </ul>
 *
 * <p>Broker URL and queue name come from {@link MqConfig}.
 */
public class HeartbeatConsumer {
    /**ActiveMQ's default dead-letter destination. */
    public static final String DEAD_LETTER_QUEUE = "ActiveMQ.DLQ";

    private static final Logger Log = LoggerFactory.getLogger(HeartbeatConsumer.class);

    private final ObjectMapper mapper = new ObjectMapper();
}
