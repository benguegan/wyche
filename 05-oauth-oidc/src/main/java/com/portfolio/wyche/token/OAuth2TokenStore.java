package com.portfolio.wyche.token;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertPathValidator;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.util.Base64;
import java.util.Optional;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import spark.Request;

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
        "TLS_AES_128_GCM_SHA256",
        "TLS_AES_256_GCM_SHA384",
        "TLS_CHACHA20_POLY1305_SHA256",
        // TLS 1.2 cipher suites
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"
    });
    sslParams.setUseCipherSuitesOrder(true);
    sslParams.setEndpointIdentificationAlgorithm("HTTPS");

    try {
      var trustedCerts = KeyStore.getInstance("PKCS12");
      trustedCerts.load(new FileInputStream("localhost.ca.12"), "changeit".toCharArray());
      var tmf = TrustManagerFactory.getInstance("PKIX");
      var pkixParams = new PKIXBuilderParameters(trustedCerts, null);
      var revocationChecker = (PKIXRevocationChecker) CertPathValidator
          .getInstance("PKIX")
          .getRevocationChecker();
      pkixParams.addCertPathChecker(revocationChecker);
      var tmParams = new CertPathTrustManagerParameters(pkixParams);
      tmf.init(tmParams);
      var sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, tmf.getTrustManagers(), null);

      this.httpClient = HttpClient.newBuilder()
          .sslParameters(sslParams)
          .sslContext(sslContext)
          .build();

    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException();
    }
  }

  @Override
  public String create(Request request, Token token) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<Token> read(Request request, String tokenId) {

  }

  private Optional<Token> processResponse(JSONObject response) {

  }

  @Override
  public void revoke(Request request, String tokenId) {
    throw new UnsupportedOperationException();
  }

}
