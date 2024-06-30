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

    // todo: tuning CHANNEL_COUNT and QUEUE_COUNT
    private static final int CHANNEL_COUNT = 510; // channel 数量与最大并发线程数匹配 (可以稍大一点), 一个 connection 最好不超过 1000 channels
    private static final int QUEUE_COUNT = 100; // 平均每个队列会收到 200,000 / 100 = 2000 条消息
    private static final String EXCHANGE_NAME = "A2_directExchange";
    private static final String HOST = "52.43.7.237"; // change to rabbitmq's ip
    private static final String USER = "admin";
    private static final String PASSWORD = "123456";
    private final Connection connection;
    private final FixedSizeChannelPool channelPool;

//    private final AtomicInteger messageCounter;

//    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);
//
//    private static final ThreadLocal<Integer> threadLocalCounter = ThreadLocal.withInitial(() -> {
//        // 为每个线程分配一个唯一的起始索引
//        return THREAD_COUNTER.getAndIncrement() % QUEUE_COUNT;
//    });

    public MqRepoImpl() throws Exception {
        System.out.println("init MqRepoImpl");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setUsername(USER);
        factory.setPassword(PASSWORD);
        this.connection = factory.newConnection();
        this.channelPool = new FixedSizeChannelPool(connection, CHANNEL_COUNT);
//        this.messageCounter = new AtomicInteger(0);

        initializeExchangeAndQueues();
    }

    private void initializeExchangeAndQueues() throws Exception {
        try (Channel channel = connection.createChannel()) {
            // 声明交换机
            channel.exchangeDeclare(EXCHANGE_NAME, "direct", false);
            // 声明队列并绑定到交换机
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
            // 1. 轮训的策略
            // int queueIndex = messageCounter.getAndIncrement() % QUEUE_COUNT;
            // 2. ThreadLocal 计数器
            // int queueIndex = threadLocalCounter.get();
            // threadLocalCounter.set((queueIndex + 1) % QUEUE_COUNT);
            // 3. 随机的策略：ThreadLocalRandom 随机性和性能都比 Random 更好，不需要手动 remove
            int queueIndex = ThreadLocalRandom.current().nextInt(QUEUE_COUNT);
            String routingKey = "queue_" + queueIndex;
            // 默认是瞬时消息
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
