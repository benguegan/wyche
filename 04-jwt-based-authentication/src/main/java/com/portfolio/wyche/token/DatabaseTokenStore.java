package com.portfolio.wyche.token;

import static com.portfolio.wyche.token.CookieTokenStore.sha256;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.dalesbred.Database;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;

public class DatabaseTokenStore implements ConfidentialTokenStore {

  private static final Logger logger = LoggerFactory.getLogger(DatabaseTokenStore.class);

  private final Database database;
  private final SecureRandom secureRandom;

  public DatabaseTokenStore(Database database) {
    this.database = database;
    this.secureRandom = new SecureRandom();

    Executors.newSingleThreadScheduledExecutor()
        .scheduleAtFixedRate(this::deleteExpiredTokens, 10, 10, TimeUnit.MINUTES);
  }

  private String randomId() {
    var bytes = new byte[20];
    secureRandom.nextBytes(bytes);

    return Base64url.encode(bytes);
  }

  @Override
  public String create(Request request, Token token) {
    var tokenId = randomId();
    var attrs = new JSONObject(token.attributes).toString();

    database.updateUnique("insert into tokens(token_id, user_id, expiry, attributes) " +
        "values(?,?,?,?)",
        hash(tokenId), token.username, token.expiry, attrs);

    return tokenId;
  }

  @Override
  public Optional<Token> read(Request request, String tokenId) {
    return database.findOptional(this::readToken,
        "select user_id, expiry, attributes " +
            "from tokens " +
            "where token_id = ?",
        hash(tokenId));
  }

  @Override
  public void revoke(Request request, String tokenId) {
    database.update("delete from tokens " +
        "where otken_id = ?",
        hash(tokenId));
  }

  private String hash(String tokenId) {
    var hash = sha256(tokenId);

    return Base64url.encode(hash);
  }

  private Token readToken(ResultSet resultSet) throws SQLException {
    var username = resultSet.getString(1);
    var expiry = resultSet.getTimestamp(2).toInstant();
    var json = new JSONObject(resultSet.getString(3));
    var token = new Token(expiry, username);

    for (var key : json.keySet()) {
      token.attributes.put(key, json.getString(key));
    }

    return token;
  }

  public void deleteExpiredTokens() {
    var deleted = database.update("delete from tokens " +
        "where expiry < current_timestamp");
    logger.info("Delete {} expired tokens", deleted);
  }
}
