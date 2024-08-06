package domain;

/**
 * @author Rebecca Zhang
 * Created on 2024-08-02
 */
public interface DbRepository extends AutoCloseable {

    // GET/resorts/{resortID}/seasons/{seasonID}/day/{dayID}/skiers
    int getNumberOfUniqueSkiersAtResortSeasonDay(int resortId, int seasonId, int dayId) throws Exception;

    // GET/skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}
    int getTotalVerticalForSkierAtDay(int resortId, int seasonId, int dayId, int skierId) throws Exception;

    // GET/skiers/{skierID}/vertical
    // query: ?resort=1
    String getTotalVerticalForSkierAtResort(int skierID, int resortId) throws Exception;
    // query: ?resort=1&season=2024
    String getTotalVerticalForSkierAtResort(int skierID, int resortId, int seasonId) throws Exception;

}
