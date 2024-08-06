package apiPresentation;/**
 * @author Rebecca Zhang
 * Created on 2024-08-02
 */


import apiPresentation.dto.out.SkierOutDto;
import com.google.gson.Gson;
import domain.DbRepository;
import infrastructure.mongoDB.DbRepositoryFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;


public class ResortServlet extends HttpServlet {

    private static final Gson gson = new Gson();

    private static final Pattern pattern = Pattern.compile("[0-9]*");

    private DbRepository dbRepository;

    /**
     * @throws ServletException ServletException is handled by the Web container
     *                          Employ factory to create instance of MqRepository's implementation class -> decouple and avoid direct dependency on infrastructure
     */
    @Override
    public void init() throws ServletException {
        System.out.println("init ResortServlet");
        super.init();
        try {
            this.dbRepository = DbRepositoryFactory.createDbRepository();
        } catch (Exception e) {
            String errorMessage = "Error: failed to initialize ResortServlet!";
            System.err.println(errorMessage);
            throw new ServletException(errorMessage, e);
        }
    }

    @Override
    public void destroy() {
        System.out.println("destroy ResortServlet");
        try {
            dbRepository.close();
        } catch (Exception e) {
            String errorMessage = "Error: failed to close ResortServlet!";
            System.err.println(errorMessage);
        }
        super.destroy();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        String urlPath = req.getPathInfo();
        // GET/resorts/{resortID}/seasons/{seasonID}/day/{dayID}/skiers
        // getNumberOfUniqueSkiersAtResortSeasonDay
        if (urlPath == null || urlPath.isEmpty()) {
            handleInvalidInput(res, "url");
            return;
        }
        String[] urlParts = urlPath.split("/");
        if (urlParts.length != 7 || !urlParts[2].equals("seasons") || !urlParts[4].equals("day") || !urlParts[6].equals("skiers")) {
            handleInvalidInput(res, "url");
            return;
        }
        if (!pattern.matcher(urlParts[1]).matches() || !pattern.matcher(urlParts[3]).matches() || !pattern.matcher(urlParts[5]).matches()) {
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
        int getResult = 0;
        try {
            getResult = dbRepository.getNumberOfUniqueSkiersAtResortSeasonDay(resortId, seasonId, dayId);
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

}
