package apiPresentation; /**
 * @author Rebecca Zhang
 * Created on 2024-06-01
 */

import apiPresentation.dto.in.SkierInDto;
import apiPresentation.dto.out.SkierOutDto;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import domain.LifeRide;
import domain.MqRepository;
import infrastructure.rabbitMq.MqRepositoryFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Servlet 是单例
// Laze: Servlet 的实例创建和 init() 方法的调用推迟到第一次请求到达时
// Eager: Web 容器部署时立即初始化，这发生在任何请求到达之前
@WebServlet(name = "api.SkierServlet", urlPatterns = {"/skiers/*"}, loadOnStartup = 1)
public class SkierServlet extends HttpServlet {

    private static final Gson gson = new Gson();
    private static final Pattern pattern = Pattern.compile("[0-9]*");
    private MqRepository mqRepository;


    // ServletException 由 Web 容器处理
    @Override
    public void init() throws ServletException {
        super.init();
        // 使用工厂类创建 MqRepository 实现类的实例 -> decouple, 避免 controller 直接依赖 infrastructure
        try {
            this.mqRepository = MqRepositoryFactory.createMqRepository();
        } catch (Exception e) {
            String errorMessage = "Failed to initialize MqRepository";
            System.out.println(errorMessage);
            throw new ServletException(errorMessage, e);
        }
    }

    @Override
    public void destroy() {
        try {
            mqRepository.close();
        } catch (Exception e) {
            // todo: init 和 destroy 的异常如何处理？
            System.out.println("Error closing resources");
        }
        super.destroy();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        String urlPath = req.getPathInfo();
        // 1. Validate url path
        if (!isUrlValid(urlPath)) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            SkierOutDto skierOutDto = new SkierOutDto("Invalid inputs: url");
            res.getWriter().write(gson.toJson(skierOutDto));
            return;
        }
        String requestBody = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        // 2. Validate request body
        if (!isRequestBodyValid(requestBody)) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            SkierOutDto skierOutDto = new SkierOutDto("Invalid inputs: request body");
            res.getWriter().write(gson.toJson(skierOutDto));
            return;
        }
        // 3. Send LifeRide as a msg to MQ
        try {
            LifeRide lifeRide = toLifeRide(urlPath, requestBody);
            String message = gson.toJson(lifeRide);
            mqRepository.sendMessageToMQ(message);
        } catch (Exception e) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            SkierOutDto skierOutDto = new SkierOutDto("Internal error: send msg to MQ");
            res.getWriter().write(gson.toJson(skierOutDto));
            return;
        }
        // 4. Return success
        res.setStatus(HttpServletResponse.SC_CREATED);
        SkierOutDto skierOutDto = new SkierOutDto("Write successful");
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
     * @return LifeRide Object
     * @Description format the incoming data (URL and JSON payload) as LifeRide Object
     */
    private LifeRide toLifeRide(String urlPath, String requestBody) {
        String[] urlParts = urlPath.split("/");
        SkierInDto skierInDto = gson.fromJson(requestBody, SkierInDto.class);
        return new LifeRide(Integer.parseInt(urlParts[1]), urlParts[3], urlParts[5], Integer.parseInt(urlParts[7]), skierInDto.getTime(), skierInDto.getLiftID());
    }

}
