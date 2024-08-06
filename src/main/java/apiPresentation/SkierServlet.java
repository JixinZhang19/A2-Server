package apiPresentation;

import apiPresentation.dto.in.SkierInDto;
import apiPresentation.dto.out.SkierOutDto;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import domain.DbRepository;
import domain.LifeRide;
import domain.MqRepository;
import infrastructure.mongoDB.DbRepositoryFactory;
import infrastructure.rabbitMq.MqRepositoryFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Rebecca Zhang
 * Created on 2024-06-25
 */

/**
 * Singleton Servlet
 * Laze initialization: Servlet instance creation and init() method invocation is delayed until the first request arrives
 * Eager initialization (load-on-startup=1): The Web container is initialized immediately upon deployment, which occurs before any request arrives
 */
public class SkierServlet extends HttpServlet {

    private static final Gson gson = new Gson();
    private static final Pattern pattern = Pattern.compile("[0-9]*");
    private MqRepository mqRepository;
    private DbRepository dbRepository;

    /**
     * @throws ServletException ServletException is handled by the Web container
     *                          Employ factory to create instance of MqRepository's implementation class
     *                          -> decouple and avoid direct dependency on infrastructure
     */
    @Override
    public void init() throws ServletException {
        System.out.println("init SkierServlet");
        super.init();
        try {
            this.mqRepository = MqRepositoryFactory.createMqRepository();
            this.dbRepository = DbRepositoryFactory.createDbRepository();
        } catch (Exception e) {
            String errorMessage = "Error: failed to initialize SkierServlet!";
            System.err.println(errorMessage);
            throw new ServletException(errorMessage, e);
        }
    }

    @Override
    public void destroy() {
        System.out.println("destroy SkierServlet");
        try {
            mqRepository.close();
            dbRepository.close();
        } catch (Exception e) {
            String errorMessage = "Error: failed to close SkierServlet!";
            System.err.println(errorMessage);
        }
        super.destroy();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        String urlPath = req.getPathInfo();
        // 1. Check get type
        if (urlPath == null || urlPath.isEmpty()) {
            handleInvalidInput(res, "url");
            return;
        }
        String[] urlParts = urlPath.split("/");
        if (urlParts.length == 8) {
            // GET/skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}
            // getTotalVerticalForSkierAtDay
            // 2. Validate url path
            if (!urlParts[2].equals("seasons") || !urlParts[4].equals("days") || !urlParts[6].equals("skiers")) {
                handleInvalidInput(res, "url");
                return;
            }
            if (!pattern.matcher(urlParts[1]).matches() || !pattern.matcher(urlParts[3]).matches() || !pattern.matcher(urlParts[5]).matches() || !pattern.matcher(urlParts[7]).matches()) {
                handleInvalidInput(res, "url");
                return;
            }
            int dayId = Integer.parseInt(urlParts[5]);
            if (dayId < 1 || dayId > 366) {
                handleInvalidInput(res, "url");
                return;
            }
            int resortId = Integer.parseInt(urlParts[1]);
            int seasonId = Integer.parseInt(urlParts[3]);
            int skierId = Integer.parseInt(urlParts[7]);
            // 3. Call according service
            int getResult = 0;
            try {
                getResult = dbRepository.getTotalVerticalForSkierAtDay(resortId, seasonId, dayId, skierId);
            } catch (Exception e) {
                handleInternalError(res, "failed to get data from DB");
                return;
            }
            if (getResult == 0) {
                handleDataNotFound(res);
                return;
            }
            res.setStatus(HttpServletResponse.SC_OK);
            SkierOutDto<Integer> skierOutDto = new SkierOutDto<>("Successful Operation", getResult);
            res.getWriter().write(gson.toJson(skierOutDto));
        } else if (urlParts.length == 3) {
            // GET/skiers/{skierID}/vertical
            // getTotalVerticalForSkierAtResort
            // 2. Validate url path and query param
            if (!urlParts[2].equals("vertical") || !pattern.matcher(urlParts[1]).matches()) {
                handleInvalidInput(res, "url");
                return;
            }
            int skierId = Integer.parseInt(urlParts[1]);
            // Get query param
            // Required
            String resort = req.getParameter("resort");
            if (resort == null || !pattern.matcher(resort).matches()) {
                handleInvalidInput(res, "query");
                return;
            }
            int resortId = Integer.parseInt(resort);
            // Optimal
            String season = req.getParameter("season");
            // 3. Call according service
            String getResult;
            if (season != null) {
                if (!pattern.matcher(season).matches()) {
                    handleInvalidInput(res, "query");
                    return;
                }
                int seasonId = Integer.parseInt(season);
                try {
                    getResult = dbRepository.getTotalVerticalForSkierAtResort(skierId, resortId, seasonId);
                    if (getResult == null) {
                        handleDataNotFound(res);
                        return;
                    }
                } catch (Exception e) {
                    handleInternalError(res, "failed to get data from DB");
                    return;
                }
            } else {
                try {
                    getResult = dbRepository.getTotalVerticalForSkierAtResort(skierId, resortId);
                    if (getResult == null) {
                        handleDataNotFound(res);
                        return;
                    }
                } catch (Exception e) {
                    handleInternalError(res, "failed to get data from DB");
                    return;
                }
            }
            res.setStatus(HttpServletResponse.SC_OK);
            SkierOutDto<String> skierOutDto = new SkierOutDto<>("Successful Operation", getResult);
            res.getWriter().write(gson.toJson(skierOutDto));
        } else {
            handleInvalidInput(res, "url");
        }
    }

    private void handleInvalidInput(HttpServletResponse res, String invalidType) throws IOException {
        res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        SkierOutDto<Object> skierOutDto = new SkierOutDto<>("Invalid inputs: " + invalidType, null);
        res.getWriter().write(gson.toJson(skierOutDto));
    }

    private void handleDataNotFound(HttpServletResponse res) throws IOException {
        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        SkierOutDto<Object> skierOutDto = new SkierOutDto<>("Data not found", null);
        res.getWriter().write(gson.toJson(skierOutDto));
    }

    private void handleInternalError(HttpServletResponse res, String errorType) throws IOException {
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        SkierOutDto<Object> skierOutDto = new SkierOutDto<>("Internal error: " + errorType, null);
        res.getWriter().write(gson.toJson(skierOutDto));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        String urlPath = req.getPathInfo();
        // 1. Validate url path
        if (!isUrlValid(urlPath)) {
            handleInvalidInput(res, "url");
            return;
        }
        String requestBody = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        // 2. Validate request body
        if (!isRequestBodyValid(requestBody)) {
            handleInvalidInput(res, "request body");
            return;
        }
        // 3. Send LifeRide as a message to MQ
        LifeRide lifeRide;
        try {
            lifeRide = toLifeRide(urlPath, requestBody);
            String message = gson.toJson(lifeRide);
            mqRepository.sendMessageToMQ(message);
        } catch (Exception e) {
            handleInternalError(res, "failed to send message to MQ");
            return;
        }
        // 4. Return success
        res.setStatus(HttpServletResponse.SC_CREATED);
        SkierOutDto<LifeRide> skierOutDto = new SkierOutDto<>("Write successful", lifeRide);
        res.getWriter().write(gson.toJson(skierOutDto));
    }

    /**
     * @param urlPath
     * @return boolean
     * @Description check if the url path is valid
     */
    private boolean isUrlValid(String urlPath) {
        // 1) Check if urlPath is empty
        if (urlPath == null || urlPath.isEmpty()) {
            return false;
        }
        // 2) Check if urlParts align with the API spec
        String[] urlParts = urlPath.split("/");
        if (urlParts.length != 8) return false;
        if (!urlParts[2].equals("seasons") || !urlParts[4].equals("days") || !urlParts[6].equals("skiers")) {
            return false;
        }
        if (!pattern.matcher(urlParts[1]).matches() || !pattern.matcher(urlParts[5]).matches() || !pattern.matcher(urlParts[7]).matches()) {
            return false;
        }
        int days = Integer.parseInt(urlParts[5]);
        return 1 <= days && days <= 366;
    }

    /**
     * @param requestBody
     * @return boolean
     * @Description check if the request body is valid
     */
    private boolean isRequestBodyValid(String requestBody) {
        // 1) Check if requestBody is empty
        if (requestBody == null || requestBody.isEmpty()) {
            return false;
        }
        // 2) Deserialize Json to Object and check if requestBody is in Json format
        SkierInDto skierInDto;
        try {
            skierInDto = gson.fromJson(requestBody, SkierInDto.class);
        } catch (JsonSyntaxException e) {
            return false;
        }
        // 3) Check if fields and attributes match
        return skierInDto.getTime() != null && skierInDto.getLiftID() != null;
    }

    /**
     * @param urlPath
     * @param requestBody
     * @return LifeRide
     * @Description format the incoming data (URL and JSON payload) as LifeRide Object
     */
    private LifeRide toLifeRide(String urlPath, String requestBody) {
        String[] urlParts = urlPath.split("/");
        SkierInDto skierInDto = gson.fromJson(requestBody, SkierInDto.class);
        return new LifeRide(
                Integer.parseInt(urlParts[1]),
                urlParts[3],
                urlParts[5],
                Integer.parseInt(urlParts[7]),
                skierInDto.getTime(),
                skierInDto.getLiftID()
        );
    }

}
