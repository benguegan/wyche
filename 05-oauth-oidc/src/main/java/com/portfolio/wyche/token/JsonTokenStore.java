package com.portfolio.wyche.token;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.json.JSONException;
import org.json.JSONObject;

import spark.Request;

public class JsonTokenStore implements TokenStore {
  @Override
  public String create(Request request, Token token) {
    var json = new JSONObject();
    json.put("sub", token.username);
    json.put("exp", token.expiry.getEpochSecond());
    json.put("aud", List.of("https://localhost:45677"));
    json.put("attrs", token.attributes);

    var jsonBytes = json.toString().getBytes(UTF_8);

    return Base64url.encode(jsonBytes);
  }

  @Override
  public Optional<Token> read(Request request, String tokenId) {
    try {
      var decoded = Base64url.decode(tokenId);
      var json = new JSONObject(new String(decoded, UTF_8));
      var expiry = Instant.ofEpochSecond(json.getInt("exp"));
      var username = json.getString("sub");
      var audience = json.getJSONArray("aud").toList();
      var attrs = json.getJSONObject("attrs");

      if (!audience.contains("https://localhost:4567")) {
        return Optional.empty();
      }

      var token = new Token(expiry, username);
      for (var key : attrs.keySet()) {
        token.attributes.put(key, attrs.getString(key));
      }

      return Optional.of(token);
    } catch (JSONException e) {
      return Optional.empty();
    }
  }

  @Override
  public void revoke(Request request, String tokenId) {
  }

}
