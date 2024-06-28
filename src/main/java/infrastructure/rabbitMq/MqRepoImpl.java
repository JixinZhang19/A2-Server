package infrastructure.rabbitMq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import domain.MqRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Rebecca Zhang
 * Created on 2024-06-25
 */
public class MqRepoImpl implements MqRepository {

    // todo: tuning CHANNEL_COUNT and QUEUE_COUNT
    private static final int CHANNEL_COUNT = 500; // channel 数量与最大并发线程数匹配，每个线程在同一时间可能需要一个 channel 来发送消息
    private static final int QUEUE_COUNT = 500; // 平均每个队列会收到 200,000 / 500 = 400 条消息
    private static final String EXCHANGE_NAME = "A2_directExchange";
    private static final String HOST = "localhost";
    private final Connection connection;
    private final FixedSizeChannelPool channelPool;
    // private final AtomicInteger messageCounter;
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private static final ThreadLocal<Integer> threadLocalCounter = ThreadLocal.withInitial(() -> {
        // 为每个线程分配一个唯一的起始索引
        return THREAD_COUNTER.getAndIncrement() % QUEUE_COUNT;
    });

    public MqRepoImpl() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        this.connection = factory.newConnection();
        this.channelPool = new FixedSizeChannelPool(connection, CHANNEL_COUNT);
        // this.messageCounter = new AtomicInteger(0);

        initializeExchangeAndQueues();
    }

    private void initializeExchangeAndQueues() throws Exception {
        try (Channel channel = connection.createChannel()) {
            // 声明交换机
            channel.exchangeDeclare(EXCHANGE_NAME, "direct", false);
            // 声明队列并绑定到交换机
            for (int i = 0; i < QUEUE_COUNT; i++) {
                String queueName = "queue_" + i;
                System.out.println(queueName);
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
            // todo: 发送消息的策略
            // 轮训到 queue 的策略
            // int queueIndex = messageCounter.getAndIncrement() % QUEUE_COUNT;
            // ThreadLocal 计数器
            int queueIndex = threadLocalCounter.get();
            threadLocalCounter.set((queueIndex + 1) % QUEUE_COUNT);
            String routingKey = "queue_" + queueIndex;
            channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes(StandardCharsets.UTF_8));
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
