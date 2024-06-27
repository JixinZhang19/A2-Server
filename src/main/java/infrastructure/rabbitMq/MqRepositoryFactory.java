package infrastructure.rabbitMq;

import domain.MqRepository;

/**
 * @author Rebecca Zhang
 * Created on 2024-06-25
 */
public class MqRepositoryFactory {

    public static MqRepository createMqRepository() throws Exception {
        return new MqRepoImpl();
    }

}
