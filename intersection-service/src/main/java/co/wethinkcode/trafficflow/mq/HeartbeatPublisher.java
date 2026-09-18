package co.wethinkcode.trafficflow.mq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

/**
 * Says "still here" on the {@code intersection-heartbeat-queue} every few seconds, so
 * intersection-watchdog can tell the difference between a quiet service and a dead one.
 *
 * <p>Messages carry a time-to-live of three intervals. That's what makes a missed
 * heartbeat visible rather than merely absent: if nobody consumes one in time the broker
 * expires it onto the dead-letter queue, which the watchdog also listens to. A heartbeat
 * that is minutes old is worse than useless, so there is no point keeping it around.
 *
 * <p>Broker URL and queue name come from {@link MqConfig}.
 */
public class HeartbeatPublisher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatPublisher.class);
    private static final int MISSED_BEATS_BEFORE_EXPIRY = 3;

    private final String brokerUrl;
    private final String queueName;
    private final long intervalMillis;
    private final IntSupplier intersectionCount;
    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicLong sequence = new AtomicLong();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "heartbeat-publisher");
        thread.setDaemon(true);
        return thread;
    });

    private Connection connection;
    private Session session;
    private MessageProducer producer;

    public HeartbeatPublisher(long intervalMillis, IntSupplier intersectionCount) {
        this(MqConfig.BROKER_URL, MqConfig.HEARTBEAT_QUEUE, intervalMillis, intersectionCount);
    }

    public HeartbeatPublisher(String brokerUrl, String queueName, long intervalMillis,
                              IntSupplier intersectionCount) {
        this.brokerUrl = brokerUrl;
        this.queueName = queueName;
        this.intervalMillis = intervalMillis;
        this.intersectionCount = intersectionCount;
    }

    /** Begins beating on a daemon thread; never blocks the caller, broker up or not. */
    public void start() {
        scheduler.scheduleAtFixedRate(this::beat, 0, intervalMillis, TimeUnit.MILLISECONDS);
        LOG.info("heartbeating to {} at {} every {}ms", queueName, brokerUrl, intervalMillis);
    }

    private void beat() {
        try {
            connect();
            TextMessage message = session.createTextMessage(json());
            producer.send(message);
        } catch (Exception e) {
            // Failing to beat is exactly what the watchdog exists to notice, so there's
            // nothing to do here but note it and let the next beat reconnect.
            LOG.warn("heartbeat to {} at {} failed: {}", queueName, brokerUrl, e.toString());
            closeQuietly();
        }
    }

    private String json() {
        return String.format(
                "{\"service\":\"intersection-service\",\"instanceId\":\"%s\",\"sequence\":%d,"
                        + "\"sentAt\":\"%s\",\"intersections\":%d,\"status\":\"UP\"}",
                instanceId, sequence.incrementAndGet(), Instant.now(), intersectionCount.getAsInt());
    }

    private void connect() throws JMSException {
        if (producer != null) {
            return;
        }
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        connection = factory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(queueName);
        producer = session.createProducer(queue);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        producer.setTimeToLive(intervalMillis * MISSED_BEATS_BEFORE_EXPIRY);
        LOG.info("connected to broker {} for heartbeats", brokerUrl);
    }

    private void closeQuietly() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (JMSException e) {
            LOG.debug("error closing broker connection", e);
        } finally {
            producer = null;
            session = null;
            connection = null;
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        closeQuietly();
    }
}
