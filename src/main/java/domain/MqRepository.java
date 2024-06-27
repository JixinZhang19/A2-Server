package domain;

import java.io.IOException;

/**
 * @author Rebecca Zhang
 * Created on 2024-06-25
 */
public interface MqRepository extends AutoCloseable {

    void sendMessageToMQ(String message) throws IOException;

}
