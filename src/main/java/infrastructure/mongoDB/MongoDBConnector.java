package infrastructure.mongoDB;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.*;
import org.bson.Document;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Accumulators.push;
import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.*;

/**
 * @author Rebecca Zhang
 * Created on 2024-08-02
 */
public class MongoDBConnector implements AutoCloseable {

    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final MongoCollection<Document> collection;

    public MongoDBConnector(String connectionString, String databaseName, String collectionName) throws MongoException {
        // MongoClient maintains an internal connection pool that can handle multiple concurrent requests
        // and automatically manages the creation, reuse, and release of connections
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .applyToConnectionPoolSettings(builder ->
                        builder.minSize(100)
                                .maxSize(500)
                                .maxConnectionIdleTime(180000, TimeUnit.MILLISECONDS)
                                .maxConnectionLifeTime(300000, TimeUnit.MILLISECONDS)
                )
                .build();
        mongoClient = MongoClients.create(settings);
        database = mongoClient.getDatabase(databaseName);
        // Check-before-create mechanism (atomicity: when inserting concurrently, the first arriving request creates
        // the collection and inserts the data, and other concurrent requests wait for the collection to be created
        // before continuing the insertion operation
        collection = database.getCollection(collectionName);
    }

    public int getTotalVerticalForSkierAtDay(int resortId, int seasonId, int dayId, int skierId) throws MongoException {
        AggregateIterable<Document> result = collection.aggregate(Arrays.asList(
                match(and(
                        eq("resortID", resortId),
                        eq("seasonID", String.valueOf(seasonId)),
                        eq("dayID", String.valueOf(dayId)),
                        eq("skierID", skierId)
                )),
                group(null, sum("totalVertical", new Document("$multiply", Arrays.asList("$liftID", 10))))
        ));

        Document doc = result.first();
        return doc != null ? doc.getInteger("totalVertical", 0) : 0;
    }

    public int getNumberOfUniqueSkiersAtResortSeasonDay(int resortId, int seasonId, int dayId) throws MongoException {
        AggregateIterable<Document> result = collection.aggregate(Arrays.asList(
                match(and(
                        eq("resortID", resortId),
                        eq("seasonID", String.valueOf(seasonId)),
                        eq("dayID", String.valueOf(dayId))
                )),
                group("$skierID"),
                count("uniqueSkiers")
        ));

        Document doc = result.first();
        return doc != null ? doc.getInteger("uniqueSkiers", 0) : 0;
    }

    public String getTotalVerticalForSkierAtResort(int skierID, int resortId) throws MongoException {
        AggregateIterable<Document> result = collection.aggregate(Arrays.asList(
                match(and(
                        eq("skierID", skierID),
                        eq("resortID", resortId)
                )),
                group("$seasonID", sum("totalVert", new Document("$multiply", Arrays.asList("$liftID", 10)))),
                project(fields(
                        excludeId(),
                        computed("seasonID", "$_id"),
                        include("totalVert")
                )),
                group(null, push("resorts", "$$ROOT")),
                project(fields(
                        excludeId(),
                        include("resorts")
                ))
        ));
        Document doc = result.first();
        return doc != null ? doc.toJson() : null;
    }

    public String getTotalVerticalForSkierAtResort(int skierID, int resortId, int seasonId) throws MongoException {
        AggregateIterable<Document> result = collection.aggregate(Arrays.asList(
                match(and(
                        eq("skierID", skierID),
                        eq("resortID", resortId),
                        eq("seasonID", String.valueOf(seasonId))
                )),
                group("$seasonID", sum("totalVert", new Document("$multiply", Arrays.asList("$liftID", 10)))),
                project(fields(
                        excludeId(),
                        computed("seasonID", "$_id"),
                        include("totalVert")
                )),
                group(null, push("resorts", "$$ROOT")),
                project(fields(
                        excludeId(),
                        include("resorts")
                ))
        ));
        Document doc = result.first();
        return doc != null ? doc.toJson() : null;
    }

    @Override
    public void close() {
        mongoClient.close();
    }

}
