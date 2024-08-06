package infrastructure.mongoDB;

import com.mongodb.MongoException;
import domain.DbRepository;

/**
 * @author Rebecca Zhang
 * Created on 2024-08-02
 */
public class DbRepoImpl implements DbRepository {

    private static final String MG_CONNECTION = "mongodb://34.220.164.23:27017"; // Change to MongoDb's ip
    private static final String MG_DATABASE = "skier";
    private static final String MG_COLLECTION = "liferide";

    private final MongoDBConnector mongoDBConnector;

    public DbRepoImpl() throws MongoException {
        System.out.println("init DbRepoImpl");
        this.mongoDBConnector = new MongoDBConnector(MG_CONNECTION, MG_DATABASE, MG_COLLECTION);
    }

    @Override
    public int getTotalVerticalForSkierAtDay(int resortId, int seasonId, int dayId, int skierId) throws MongoException {
        return mongoDBConnector.getTotalVerticalForSkierAtDay(resortId, seasonId, dayId, skierId);
    }

    @Override
    public String getTotalVerticalForSkierAtResort(int skierID, int resortId) throws MongoException {
        return mongoDBConnector.getTotalVerticalForSkierAtResort(skierID, resortId);
    }

    @Override
    public String getTotalVerticalForSkierAtResort(int skierID, int resortId, int seasonId) throws MongoException {
        return mongoDBConnector.getTotalVerticalForSkierAtResort(skierID, resortId, seasonId);
    }

    @Override
    public int getNumberOfUniqueSkiersAtResortSeasonDay(int resortId, int seasonId, int dayId) throws MongoException {
        return mongoDBConnector.getNumberOfUniqueSkiersAtResortSeasonDay(resortId, seasonId, dayId);
    }

    @Override
    public void close() {
        System.out.println("destroy DbRepoImpl");
        mongoDBConnector.close();
    }
}
