package infrastructure.rabbitMq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Rebecca Zhang
 * Created on 2024-06-26
 */
public class FixedSizeChannelPool implements AutoCloseable {

    // ConcurrentLinkedQueue is implemented by CAS（Compare-And-Swap), without explicit locks
    private final ConcurrentLinkedQueue<Channel> pool;
    private final Connection connection;
    private final int poolSize;

    public FixedSizeChannelPool(Connection connection, int poolSize) throws IOException {
        System.out.println("init FixedSizeChannelPool");
        this.pool = new ConcurrentLinkedQueue<>();
        this.connection = connection;
        this.poolSize = poolSize;
        initializePool();
    }

    private void initializePool() throws IOException {
        for (int i = 0; i < poolSize; i++) {
            pool.offer(connection.createChannel());
        }
    }

    public Channel borrowChannel() throws IOException {
        Channel channel = pool.poll();
        if (channel == null || !channel.isOpen()) {
            return connection.createChannel();
        }
        return channel;
    }

    public void returnChannel(Channel channel) throws IOException {
        if (channel != null && channel.isOpen()) {
            pool.offer(channel);
        } else {
            pool.offer(connection.createChannel());
        }
    }

    @Override
    public void close() throws Exception {
        System.out.println("destroy FixedSizeChannelPool");
        for (Channel channel : pool) {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        }
    }

}

