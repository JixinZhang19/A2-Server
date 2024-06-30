package infrastructure.rabbitMq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import domain.MqRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Rebecca Zhang
 * Created on 2024-06-25
 */
public class MqRepoImpl implements MqRepository {

    // Match the number of channels to the maximum number of concurrent threads (slightly larger to avoid competitiveness)
    private static final int CHANNEL_COUNT = 255;
    // On average, each queue receives 200,000 / 100 = 2000 messages
    private static final int QUEUE_COUNT = 100;
    private static final String EXCHANGE_NAME = "A2_directExchange";
    private static final String HOST = "54.189.167.171"; // Change to rabbitmq's ip
    private static final String USER = "admin";
    private static final String PASSWORD = "123456";
    private final Connection connection;
    private final FixedSizeChannelPool channelPool;

    public MqRepoImpl() throws Exception {
        System.out.println("init MqRepoImpl");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setUsername(USER);
        factory.setPassword(PASSWORD);
        this.connection = factory.newConnection();
        this.channelPool = new FixedSizeChannelPool(connection, CHANNEL_COUNT);
        initializeExchangeAndQueues();
    }

    private void initializeExchangeAndQueues() throws Exception {
        try (Channel channel = connection.createChannel()) {
            // 1) Declare direct exchange
            channel.exchangeDeclare(EXCHANGE_NAME, "direct", false);
            // 2) Declare queues and binds
            for (int i = 0; i < QUEUE_COUNT; i++) {
                String queueName = "queue_" + i;
                channel.queueDeclare(queueName, false, false, false, null);
                channel.queueBind(queueName, EXCHANGE_NAME, queueName);
            }
        }
    }

    @Override
    public void sendMessageToMQ(String message) throws IOException {
        Channel channel = null;
        try {
            channel = channelPool.borrowChannel();
            // Random strategy: ThreadLocalRandom‘s randomness and performance is better than Random, and o need to manually remove the ThreadLocal
            int queueIndex = ThreadLocalRandom.current().nextInt(QUEUE_COUNT);
            String routingKey = "queue_" + queueIndex;
            // The default is transient messages
            channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes(StandardCharsets.UTF_8));
        } finally {
            channelPool.returnChannel(channel);
        }
    }

    @Override
    public void close() throws Exception {
        System.out.println("destroy MqRepoImpl");
        channelPool.close();
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }

}
