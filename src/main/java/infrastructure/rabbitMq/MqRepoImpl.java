package infrastructure.rabbitMq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import domain.MqRepository;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Rebecca Zhang
 * Created on 2024-06-25
 */
public class MqRepoImpl implements MqRepository {

    private static final int CHANNEL_COUNT = 500; // channel 数量与最大并发线程数匹配
    private static final int QUEUE_COUNT = 200; // 平均每个队列会收到1,000条消息
    private static final String EXCHANGE_NAME = "A2_directExchange";
    private final Connection connection;
    private final FixedSizeChannelPool channelPool;
    private final AtomicInteger messageCounter;

    public MqRepoImpl() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        this.connection = factory.newConnection();
        this.channelPool = new FixedSizeChannelPool(connection, CHANNEL_COUNT);
        this.messageCounter = new AtomicInteger(0);

        initializeExchangeAndQueues();
    }

    private void initializeExchangeAndQueues() throws Exception {
        try (Channel channel = connection.createChannel()) {
            // 声明交换机
            channel.exchangeDeclare(EXCHANGE_NAME, "direct", false);
            // 声明队列并绑定到交换机
            for (int i = 0; i < QUEUE_COUNT; i++) {
                String queueName = "queue_" + i;
                // todo: 队列参数如何设置
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
            // 轮训到 queue 的策略
            int queueIndex = messageCounter.getAndIncrement() % QUEUE_COUNT;
            String routingKey = "queue_" + queueIndex;
            channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes());
            // todo: 需要发送成功确认后再返回吗 channel.confirmSelect(); channel.waitForConfirmsOrDie(5_000); -> 影响吞吐量
        } finally {
            channelPool.returnChannel(channel);
        }
    }

    @Override
    public void close() throws Exception {
        channelPool.close();
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }

}
