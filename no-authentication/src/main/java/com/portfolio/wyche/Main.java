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
import java.util.Set;

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

        // apply standard http security headers to all response
        afterAfter(((request, response) -> {
            // explicitly indicate he content-type
            // explicitly indicate the UTF-8 character-encoding
            response.type("application/json;charset=utf-8");
            // Set to DENY to prevent the API responses being loaded in a frame or iframe.
            response.header("X-Frame-Options", "DENY");
            // turn-off browser built-in protection against reflected XSS attacks
            // XSS protections in browser have been found to cause security vulnerabilities
            // OWASP project recommends always disabling the filter, as follows:
            response.header("X-XSS-Protection", "0");
            // Controls whether browsers and proxies can cache content in the response and
            // for how long
            response.header("Cache-Control", "no-store");
            // reduce the scope for XSS attacks by restricting where scripts can be loaded
            // from and what they can do
            // `default-src 'none'`: prevents the response from loading any scripts or
            // resources.
            // `frame-ancestors 'none'`: replacement for X-Frame-Options, this prevents the
            // response being loaded into an iframe.
            // `sandbox n/a`: disables scripts and other potentially dangerous content from
            // being executed
            response.header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox");
            // remove server information leak (jetty webserver version)
            response.header("Server", "");
            // Set to nosniff to prevent the browser guessing the correct Content-Type
            // response.header("X-Content-Type-Options", "nosniff");
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

    // remove the leak of the exception class details by changing the exception
    // handler to only return the error message (details message from the
    // exception), not the full class
    private static void badRequest(Exception ex, Request request, Response response) {
        response.status(400);
        // use a proper JSON library for all outputs
        response.body(new JSONObject().put("error", ex.getMessage()).toString());
    }

    private static void createTables(Database database) throws Exception {
        var path = Paths.get(Main.class.getResource("/schema.sql").toURI());
        database.update(Files.readString(path));
    }
}
