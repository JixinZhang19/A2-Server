package infrastructure.mongoDB;

import domain.DbRepository;

/**
 * @author Rebecca Zhang
 * Created on 2024-08-03
 */
public class DbRepositoryFactory {

    private static DbRepository instance;

    public static synchronized DbRepository createDbRepository() throws Exception {
        if (instance == null) {
            instance = new DbRepoImpl();
        }
        return instance;
    }

//    // Singleton
//
//    private static DbRepository instance;
//
//    private DbRepositoryFactory() {}
//
//    public static synchronized DbRepository createDbRepository() {
//        if (instance == null) {
//            instance = new DbRepoImpl();
//        }
//        return instance;
//    }

}
