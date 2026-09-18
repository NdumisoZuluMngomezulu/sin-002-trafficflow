package co.wethinkcode.trafficflow.mq;

import co.wethinkcode.trafficflow.CongestionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Publishes each congestion level change to the {@code congestion-topic} topic, so
 * routing-service can stop polling us.
 *
 * <p>Two deliberate choices here:
 *
 * <ul>
 *   <li><b>Sends happen off the request thread.</b> A slow or missing broker must never
 *       stall an HTTP response — congestion-service's own API keeps working whether or
 *       not anyone is listening on the topic.</li>
 *   <li><b>A failed send drops the connection rather than the service.</b> The next
 *       publish reconnects. Losing a level change is survivable: the topic carries the
 *       current level, subscribers can still fall back to {@code GET /congestion}, and
 *       the next change re-syncs everyone. Guaranteeing delivery would mean durable
 *       subscriptions and persistent messages, which is a lot of machinery for a value
 *       that is only interesting while it's current.</li>
 * </ul>
 *
 * <p>Broker URL and topic name come from {@link MqConfig}.
 */
public class CongestionPublisher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CongestionPublisher.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "congestion-publisher");
        thread.setDaemon(true);
        return thread;
    });

    private final String brokerUrl;
    private final String topicName;

    private Connection connection;
    private Session session;
    private MessageProducer producer;

    public CongestionPublisher() {
        this(MqConfig.BROKER_URL, MqConfig.TOPIC);
    }

    public CongestionPublisher(String brokerUrl, String topicName) {
        this.brokerUrl = brokerUrl;
        this.topicName = topicName;
    }

    /** Queues a level change for publication and returns immediately. */
    public void publish(CongestionState state) {
        sender.submit(() -> send(state));
    }

    private synchronized void send(CongestionState state) {
        try {
            connect();
            producer.send(session.createTextMessage(mapper.writeValueAsString(state)));
            LOG.info("published level {} ({}) to {}", state.level(), state.label(), topicName);
        } catch (Exception e) {
            LOG.warn("could not publish level {} to {} at {}: {} — dropping the connection so the next "
                    + "change reconnects", state.level(), topicName, brokerUrl, e.toString());
            closeQuietly();
        }
    }

    private void connect() throws JMSException {
        if (producer != null) {
            return;
        }
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        connection = factory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(topicName);
        producer = session.createProducer(topic);
        // The current level is only interesting while it's current, so there's nothing
        // worth writing to disk for a subscriber that wasn't listening.
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        LOG.info("connected to broker {}, publishing to topic {}", brokerUrl, topicName);
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
        sender.shutdown();
        try {
            if (!sender.awaitTermination(2, TimeUnit.SECONDS)) {
                sender.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sender.shutdownNow();
        }
        synchronized (this) {
            closeQuietly();
        }
    }
}
