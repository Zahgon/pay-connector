package uk.gov.pay.connector.app.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The admin port's operational tasks under {@code /tasks}, run by POST: {@code gc} runs the garbage collector
 * ({@code runs} times) and {@code log-level} sets the level of the named loggers, for a {@code duration} if one is
 * given. GET lists the tasks; GET on a task is not allowed; any other path is not found.
 */
public class AdminTaskServlet extends HttpServlet {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AdminTaskServlet.class);
    private static final String CONTENT_TYPE = "text/plain;charset=UTF-8";
    private static final List<String> TASKS = List.of("gc", "log-level");

    private transient Timer timer;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (Optional.ofNullable(req.getPathInfo()).filter(s -> !s.isEmpty()).isEmpty()) {
            try (PrintWriter output = resp.getWriter()) {
                resp.setContentType(CONTENT_TYPE);
                TASKS.stream().sorted().forEach(output::println);
            }
        } else if (TASKS.contains(req.getPathInfo().substring(1))) {
            resp.reset();
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        } else {
            resp.reset();
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String task = req.getPathInfo() == null ? null : req.getPathInfo().substring(1);
        if (task == null || !TASKS.contains(task)) {
            resp.reset();
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        resp.setContentType(CONTENT_TYPE);
        Map<String, List<String>> parameters = new HashMap<>();
        req.getParameterMap().forEach((name, values) -> parameters.put(name, Arrays.asList(values)));
        try (PrintWriter output = resp.getWriter()) {
            try {
                if ("gc".equals(task)) {
                    runGc(parameters, output);
                } else {
                    setLogLevel(parameters, output);
                }
            } catch (RuntimeException e) {
                LOGGER.error("Error running {}", task, e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                output.println();
                output.println(e.getMessage());
            }
        }
    }

    private static void runGc(Map<String, List<String>> parameters, PrintWriter output) {
        int runs = 1;
        List<String> requested = parameters.get("runs");
        if (requested != null && !requested.isEmpty()) {
            try {
                runs = Integer.parseInt(requested.getFirst());
            } catch (NumberFormatException e) {
                runs = 1;
            }
        }
        for (int i = 0; i < runs; i++) {
            output.println("Running GC...");
            output.flush();
            Runtime.getRuntime().gc();
        }
        output.println("Done!");
    }

    private void setLogLevel(Map<String, List<String>> parameters, PrintWriter output) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        List<String> levels = parameters.getOrDefault("level", List.of());
        Level level = levels.isEmpty() ? null : Level.valueOf(levels.getFirst());
        List<String> durations = parameters.getOrDefault("duration", List.of());
        Duration duration = durations.isEmpty() ? null : Duration.parse(durations.getFirst());
        for (String name : parameters.getOrDefault("logger", List.of())) {
            Logger logger = context.getLogger(name);
            String message = String.format("Configured logging level for %s to %s", name, level);
            if (level != null && duration != null) {
                long millis = duration.toMillis();
                timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        logger.setLevel(null);
                    }
                }, millis);
                message += String.format(" for %s milliseconds", millis);
            }
            logger.setLevel(level);
            output.println(message);
            output.flush();
        }
    }

    private synchronized Timer timer() {
        if (timer == null) {
            timer = new Timer("log-level-reset", true);
        }
        return timer;
    }

    @Override
    public void destroy() {
        if (timer != null) {
            timer.cancel();
        }
    }
}
