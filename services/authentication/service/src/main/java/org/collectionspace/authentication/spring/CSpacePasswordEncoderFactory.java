package org.collectionspace.authentication.spring;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class CSpacePasswordEncoderFactory {
  private static PasswordEncoder instance = null;

  public static PasswordEncoder createDefaultPasswordEncoder() {
    if (instance == null) {
      Map<String, PasswordEncoder> encoders = new HashMap<String, PasswordEncoder>();

      // Passwords in CollectionSpace pre-7.3 were SHA-256 hashed and Base64 encoded. Continue to
      // support these.
      MessageDigestPasswordEncoder legacyPasswordEncoder = new MessageDigestPasswordEncoder("SHA-256");
      legacyPasswordEncoder.setEncodeHashAsBase64(true);

      encoders.put("bcrypt", new BCryptPasswordEncoder());
      encoders.put("noop", NoOpPasswordEncoder.getInstance());
      encoders.put("SHA-256", legacyPasswordEncoder);

      instance = new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    return instance;
  }
}
