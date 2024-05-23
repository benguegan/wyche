package com.portfolio.wyche;

import static spark.Spark.afterAfter;
import static spark.Spark.before;
import static spark.Spark.delete;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.internalServerError;
import static spark.Spark.notFound;
import static spark.Spark.post;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.dalesbred.Database;
import org.dalesbred.result.EmptyResultException;
import org.h2.jdbcx.JdbcConnectionPool;
import org.json.JSONException;
import org.json.JSONObject;

import com.portfolio.wyche.controller.ModeratorController;
import com.portfolio.wyche.controller.SpaceController;

import spark.Request;
import spark.Response;

public class Main {
    public static void main(String[] args) throws Exception {
        var datasource = JdbcConnectionPool.create("jdbc:h2:mem:wyche", "wyche", "password");
        var database = Database.forDataSource(datasource);
        createTables(database);

        datasource = JdbcConnectionPool.create("jdbc:h2:mem:wyche", "api_user", "password");
        database = Database.forDataSource(datasource);
        var spaceController = new SpaceController(database);

        /* -------------------------------------------------------------------------- */
        /* filter */
        /* -------------------------------------------------------------------------- */
        before((request, response) -> {
            if (request.requestMethod().equals("POST") && !"application/json".equals(request.contentType())) {
                halt(415, new JSONObject().put("error", "Only application/json supported").toString());
            }
        });

        afterAfter(((request, response) -> {
            response.type("application/json;charset=utf-8");
            response.header("X-Frame-Options", "DENY");
            response.header("X-XSS-Protection", "0");
            response.header("Cache-Control", "no-store");
            response.header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox");
            response.header("Server", "");
        }));

        /* -------------------------------------------------------------------------- */
        /* space controller */
        /* -------------------------------------------------------------------------- */
        post("/spaces", spaceController::createSpace);
        post("/spaces/:spaceId/messages", spaceController::postMessage);
        get("/spaces/:spaceId/messages/:msgId", spaceController::readMessage);
        get("/spaces/:spaceId/messages", spaceController::findMessages);

        /* -------------------------------------------------------------------------- */
        /* moderator controller */
        /* -------------------------------------------------------------------------- */
        var moderatorController = new ModeratorController(database);
        delete("/spaces/:spaceId/messages/:msgId", moderatorController::deletePost);

        /* -------------------------------------------------------------------------- */
        /* error handling */
        /* -------------------------------------------------------------------------- */
        internalServerError(new JSONObject().put("error", "internal server error").toString());
        notFound(new JSONObject().put("error", "not found").toString());

        exception(IllegalArgumentException.class, Main::badRequest);
        exception(JSONException.class, Main::badRequest);
        exception(EmptyResultException.class, (err, req, res) -> res.status(404));
    }

    private static void badRequest(Exception ex, Request request, Response response) {
        response.status(400);
        response.body(new JSONObject().put("error", ex.getMessage()).toString());
    }

    private static void createTables(Database database) throws Exception {
        var path = Paths.get(Main.class.getResource("/schema.sql").toURI());
        database.update(Files.readString(path));
    }
}
