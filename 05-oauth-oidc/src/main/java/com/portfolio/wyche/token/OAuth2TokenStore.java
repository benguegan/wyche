package com.portfolio.wyche.token;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.util.Base64;

import javax.net.ssl.SSLParameters;

public class OAuth2TokenStore implements SecureTokenStore {

  private final URI introspectionEndpoint;
  private final String authorization;
  private final HttpClient httpClient;

  public OAuth2TokenStore(URI introspectionEndpoint, String clientId, String clientSecret) {
    this.introspectionEndpoint = introspectionEndpoint;
    var credentials = URLEncoder.encode(clientId, UTF_8) +
        ":" +
        URLEncoder.encode(clientSecret, UTF_8);
    this.authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(UTF_8));
    var sslParams = new SSLParameters();
    sslParams.setProtocols(new String[] { "TLSv1.3", "TLSv1.2" });
    sslParams.setCipherSuites(new String[] {
        // TLS 1.3 cipher suites

        // TLS 1.2 cipher suites
    });

  }

}
