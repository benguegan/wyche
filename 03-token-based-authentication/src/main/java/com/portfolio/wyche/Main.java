package com.portfolio.wyche;

import static spark.Service.SPARK_DEFAULT_PORT;
import static spark.Spark.afterAfter;
import static spark.Spark.before;
import static spark.Spark.delete;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.internalServerError;
import static spark.Spark.notFound;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.secure;
import static spark.Spark.staticFiles;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Set;

import org.dalesbred.Database;
import org.dalesbred.result.EmptyResultException;
import org.h2.jdbcx.JdbcConnectionPool;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.util.concurrent.RateLimiter;
import com.portfolio.wyche.controller.AuditController;
import com.portfolio.wyche.controller.ModeratorController;
import com.portfolio.wyche.controller.SpaceController;
import com.portfolio.wyche.controller.TokenController;
import com.portfolio.wyche.controller.UserController;
import com.portfolio.wyche.filter.CorsFilter;
import com.portfolio.wyche.token.DatabaseTokenStore;
import com.portfolio.wyche.token.HmacTokenStore;

import spark.Request;
import spark.Response;

public class Main {
    public static void main(String[] args) throws Exception {
        staticFiles.location("/public");
        // enable https
        secure("localhost.p12", "changeit", null, null);
        port(args.length > 0 ? Integer.parseInt(args[0]) : SPARK_DEFAULT_PORT);

        var datasource = JdbcConnectionPool.create("jdbc:h2:mem:wyche", "wyche", "password");
        var database = Database.forDataSource(datasource);
        createTables(database);

        datasource = JdbcConnectionPool.create("jdbc:h2:mem:wyche", "api_user", "password");
        database = Database.forDataSource(datasource);

        var spaceController = new SpaceController(database);
        var moderatorController = new ModeratorController(database);
        var userController = new UserController(database);
        var auditController = new AuditController(database);
        var rateLimiter = RateLimiter.create(2.0d);

        var keyPassword = System.getProperty("keystore.password", "changeit").toCharArray();
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream("keystore.p12"), keyPassword);
        var macKey = keyStore.getKey("hmac-key", keyPassword);
        var databaseTokenStore = new DatabaseTokenStore(database);
        var tokenStore = new HmacTokenStore(databaseTokenStore, macKey);
        var tokenController = new TokenController(tokenStore);

        /* -------------------------------------------------------------------------- */
        /* filter */
        /* -------------------------------------------------------------------------- */
        /* ------------------------------ rate limiter ------------------------------ */
        before((request, response) -> {
            if (!rateLimiter.tryAcquire()) {
                // `Retry-After` header indicate how many seconds the client should wait before
                // trying again
                response.header("Retry-After", "2");
                // http code used to indicate that rate-limiting has been applied and that the
                // client should try the request again later
                halt(429);
            }
        });

        /* ---------------------------------- cors ---------------------------------- */
        before(new CorsFilter(Set.of("https://localhost:9999")));

        /* --------------------- standard http security headers --------------------- */
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
            // - `default-src 'none'`: prevents the response from loading any scripts or
            // resources
            // - `frame-ancestors 'none'`: replacement for X-Frame-Options, this prevents
            // the response being loaded into an iframe
            // - `sandbox n/a`: disables scripts and other potentially dangerous content
            // from being executed
            response.header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox");
            // remove server information leak (jetty webserver version)
            response.header("Server", "");
            // Set to `nosniff` to prevent the browser guessing the correct Content-Type
            // response.header("X-Content-Type-Options", "nosniff");
            // HTTP Strict-Transport-Security (HSTS) header instruct the browser to always
            // use the HTTPS version in the future
            // Note: on localhost it prevents from running the development server over plain
            // HTTP
            // response.header("Strict-Transport-Security", "max-age=31536000");
        }));

        /* ----------------------------- authentication ----------------------------- */
        before(userController::authenticate);
        before(tokenController::validateToken);

        /* -------------------------------- audit log ------------------------------- */
        // record access log
        before(auditController::auditRequestStart);
        afterAfter(auditController::auditRequestEnd);

        /* -------------------------------------------------------------------------- */
        /* session */
        /* -------------------------------------------------------------------------- */
        before("/sessions", userController::requireAuthentication);
        post("/sessions", tokenController::login);
        delete("/sessions", tokenController::logout);

        /* -------------------------------------------------------------------------- */
        /* space controller */
        /* -------------------------------------------------------------------------- */
        before("/spaces", userController::requireAuthentication);
        post("/spaces", spaceController::createSpace);

        before("/spaces/:spaceId/messages", userController.requirePermission("POST", "w"));
        post("/spaces/:spaceId/messages", spaceController::postMessage);

        before("/spaces/:spaceId/messages/*", userController.requirePermission("GET", "r"));
        get("/spaces/:spaceId/messages/:msgId", spaceController::readMessage);

        before("/spaces/:spaceId/messages", userController.requirePermission("GET", "r"));
        get("/spaces/:spaceId/messages", spaceController::findMessages);

        before("/spaces/:spaceId/members", userController.requirePermission("POST", "rwd"));
        post("/spaces/:spaceId/members", spaceController::addMember);

        /* -------------------------------------------------------------------------- */
        /* moderator controller */
        /* -------------------------------------------------------------------------- */
        before("/spaces?:spaceId/messages/*", userController.requirePermission("DELETE", "d"));
        delete("/spaces/:spaceId/messages/:msgId", moderatorController::deletePost);

        /* -------------------------------------------------------------------------- */
        /* other controllers */
        /* -------------------------------------------------------------------------- */
        get("/logs", auditController::readAuditLog);
        post("/users", userController::registerUser);

        before("/expired_tokens", userController::requireAuthentication);
        delete("/expired_tokens", (request, response) -> {
            databaseTokenStore.deleteExpiredTokens();

            return new JSONObject();
        });

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
    // handler to only return the error message (i.e., details message from the
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
