package com.portfolio.wyche.token;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import com.lambdaworks.codec.Base64;

import spark.Request;

// Use AES in unauthenticated counter mode
public class UnauthenticatedEncryptionStore implements ConfidentialTokenStore {

  private final Key encKey;
  private final TokenStore delegate;

  public UnauthenticatedEncryptionStore(Key encKey, TokenStore delegate) {
    this.encKey = encKey;
    this.delegate = delegate;
  }

  @Override
  public String create(Request request, Token token) {
    var tokenId = delegate.create(request, token);

    return encrypt(tokenId.getBytes(UTF_8));
  }

  @Override
  public Optional<Token> read(Request request, String tokenId) {
    return decrypt(tokenId)
        .flatMap(d -> delegate.read(request, d));
  }

  @Override
  public void revoke(Request request, String tokenId) {
    decrypt(tokenId)
        .ifPresent(d -> delegate.revoke(request, d));
  }

  private String encrypt(byte[] data) {
    try {
      var cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, encKey);
      var cipherText = cipher.doFinal(data);
      var iv = cipher.getIV();

      return Base64url.encode(iv) + '.' + Base64url.encode(cipherText);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<String> decrypt(String encrypted) {
    var index = encrypted.indexOf('.');
    if (index == -1) {
      return Optional.empty();
    }

    var iv = Base64url.decode(encrypted.substring(0, index));
    var cipherText = Base64url.decode(encrypted.substring(index + 1));

    try {
      var cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, encKey, new IvParameterSpec(iv));
      var plainText = cipher.doFinal(cipherText);

      return Optional.of(new String(plainText, UTF_8));
    } catch (GeneralSecurityException e) {
      return Optional.empty();
    }
  }
}
