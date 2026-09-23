package co.wethinkcode.trafficflow.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.function.IntConsumer;

/**
 * Listens on the {@code congestion-topic} topic and hands each level change to
 * routing-service, replacing the per-request poll of congestion-service.
 *
 * <p>Connects over ActiveMQ's failover transport so the subscription survives a broker
 * restart, and so routing-service can start before the broker does. Until the first
 * message arrives — or if the broker never comes up — {@code CongestionProvider} falls
 * back to the REST call, which is why a broker being down degrades this service rather
 * than breaking it.
 *
 * <p>Broker URL and topic name come from {@link MqConfig}.
 */
public class CongestionSubscriber implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CongestionSubscriber.class);

    private final String brokerUrl;
    private final String topicName;
    private final IntConsumer onLevel;

    private Connection connection;

    public CongestionSubscriber(IntConsumer onLevel) {
        this(MqConfig.BROKER_URL, MqConfig.TOPIC, onLevel);
    }

    public CongestionSubscriber(String brokerUrl, String topicName, IntConsumer onLevel) {
        this.brokerUrl = brokerUrl;
        this.topicName = topicName;
        this.onLevel = onLevel;
    }

    /**
     * Establishes the subscription on a background thread and returns immediately.
     *
     * <p>The failover transport blocks until it finds a broker, so connecting inline
     * would hang startup whenever the broker isn't up yet. Routing-service needs to
     * start regardless — it still answers requests using the REST fallback.
     */
    public void start() {
        Thread connector = new Thread(this::connect, "congestion-subscriber");
        connector.setDaemon(true);
        connector.start();
    }

    private void connect() {
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(failoverUrl());
            connection = factory.createConnection();
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(topicName);
            MessageConsumer consumer = session.createConsumer(topic);
            consumer.setMessageListener(this::onMessage);
            LOG.info("subscribed to topic {} at {}", topicName, brokerUrl);
        } catch (JMSException e) {
            LOG.warn("could not subscribe to {} at {}: {} — falling back to polling congestion-service",
                    topicName, brokerUrl, e.toString());
        }
    }

    private void onMessage(javax.jms.Message message) {
        if (!(message instanceof TextMessage text)) {
            LOG.warn("ignoring non-text message on {}: {}", topicName, message.getClass().getSimpleName());
            return;
        }
        try {
            JsonNode level = new ObjectMapper().readTree(text.getText()).get("level");
            if (level == null || !level.canConvertToInt()) {
                LOG.warn("ignoring message on {} with no usable level: {}", topicName, text.getText());
                return;
            }
            LOG.info("congestion level {} received from {}", level.asInt(), topicName);
            onLevel.accept(level.asInt());
        } catch (Exception e) {
            // A bad message must not kill the subscription - log it and keep listening.
            LOG.warn("could not read a message on {}: {}", topicName, e.toString());
        }
    }

    /** Wraps the configured broker URL so a broker restart doesn't end the subscription. */
    private String failoverUrl() {
        return "failover:(" + brokerUrl + ")?initialReconnectDelay=1000&maxReconnectDelay=10000";
    }

    @Override
    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (JMSException e) {
            LOG.debug("error closing broker connection", e);
        }
    }
}
