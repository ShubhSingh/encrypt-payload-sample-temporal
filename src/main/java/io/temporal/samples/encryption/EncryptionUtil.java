package io.temporal.samples.encryption;

import com.codingrodent.jackson.crypto.CryptoModule;
import com.codingrodent.jackson.crypto.EncryptionService;
import com.codingrodent.jackson.crypto.PasswordCryptoContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.m3.util.ImmutableMap;
import io.temporal.samples.model.Signup;
import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
@AllArgsConstructor
public class EncryptionUtil {

  private static final Logger log = LoggerFactory.getLogger(EncryptionUtil.class);

  private static final ObjectMapper MAPPER_WITH_LATEST_SECRET = new ObjectMapper();
  private static final ObjectMapper MAPPER_WITH_OLD_SECRET_1 = new ObjectMapper();
  private static final ObjectMapper MAPPER_WITH_OLD_SECRET_2 = new ObjectMapper();

  /**
   * this is a map containing 3 secret keys and passwords for jackson crypto encryption so that key
   * rotation can be done in future probably using AWS KMS
   */
  private static final ImmutableMap<String, String> SECRETS =
      ImmutableMap.of(
          "latest", "SecretPassword1", "oldKey1", "SecretPassword2", "oldKey2", "SecretPassword3");

  /** returns encrypted String */
  public String getEncryptedSignup(Signup request) throws JsonProcessingException {

    ObjectMapper mapper = getObjectMapperWithCryptoEnabled("latest");

    String encryptedRequest = mapper.writeValueAsString(request);
    log.info("Encrypted Signup request: {}", encryptedRequest);
    return encryptedRequest;
  }

  /** returns decrypted Object */
  public Signup getDecryptedSignup(String signupEncryptedRequest) {
    log.info("Attempt to decrypt the encrypted request: {}", signupEncryptedRequest);

    Signup signupRequest = null;
    int failCount = 0;

    for (String secretKey : SECRETS.keySet()) {
      ObjectMapper mapper = getObjectMapperWithCryptoEnabled(secretKey);

      try {
        signupRequest = mapper.readValue(signupEncryptedRequest, Signup.class);
      } catch (IOException e) {
        failCount++;
        if (failCount == 3) {
          log.error("Failed to decrypt the request: {}", signupEncryptedRequest);
        }
        log.warn("Decryption failed try again to decrypt using different secret key");
        continue;
      }
    }

    log.info("Sending back the decrypted request: {}", signupRequest);
    return signupRequest;
  }

  public ObjectMapper getObjectMapperWithCryptoEnabled(String secretKey) {
    // if you want to have static final object mapper then have 3 separate ObjectMapper bcoz unable
    // to replace registered crypto module

    EncryptionService encryptionService = null;

    switch (secretKey) {
      case "oldKey1":
        encryptionService =
            new EncryptionService(
                MAPPER_WITH_OLD_SECRET_1, new PasswordCryptoContext(SECRETS.get("oldKey1")));
        MAPPER_WITH_OLD_SECRET_1.registerModule(
            new CryptoModule().addEncryptionService(encryptionService));
        return MAPPER_WITH_OLD_SECRET_1;
      case "oldKey2":
        encryptionService =
            new EncryptionService(
                MAPPER_WITH_OLD_SECRET_2, new PasswordCryptoContext(SECRETS.get("oldKey2")));
        MAPPER_WITH_OLD_SECRET_2.registerModule(
            new CryptoModule().addEncryptionService(encryptionService));
        return MAPPER_WITH_OLD_SECRET_2;
      default:
        encryptionService =
            new EncryptionService(
                MAPPER_WITH_LATEST_SECRET, new PasswordCryptoContext(SECRETS.get("latest")));
        MAPPER_WITH_LATEST_SECRET.registerModule(
            new CryptoModule().addEncryptionService(encryptionService));
        return MAPPER_WITH_LATEST_SECRET;
    }
  }
}
