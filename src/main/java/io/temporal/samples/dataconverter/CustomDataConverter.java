package io.temporal.samples.dataconverter;

import com.codingrodent.jackson.crypto.CryptoModule;
import com.codingrodent.jackson.crypto.EncryptionService;
import com.codingrodent.jackson.crypto.PasswordCryptoContext;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.temporal.common.converter.*;
import java.util.concurrent.atomic.AtomicReference;

public class CustomDataConverter extends DefaultDataConverter {

  private static final ObjectMapper mapper = new ObjectMapper();

  static {
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    mapper.registerModule(new JavaTimeModule());
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    // Enable encryption using jackson crypto
    // TODO: Find a way to rotate SecretKey and try 3 different keys
    EncryptionService encryptionService =
        new EncryptionService(mapper, new PasswordCryptoContext("SecretPass1"));
    mapper.registerModule(new CryptoModule().addEncryptionService(encryptionService));
  }

  private static final AtomicReference<DataConverter> defaultDataConverterInstance =
      new AtomicReference<>(
          // Order is important as the first converter that can convert the payload is used
          new CustomDataConverter(
              new NullPayloadConverter(),
              new ByteArrayPayloadConverter(),
              new ProtobufJsonPayloadConverter(),
              // Initialize Jackson Json PayloadConverter with custom Object Mapper having jackson
              // crypto encryption enabled
              new JacksonJsonPayloadConverter(mapper)));

  static DataConverter getDefaultInstance() {
    return defaultDataConverterInstance.get();
  }

  public CustomDataConverter(PayloadConverter... converters) {
    super(converters);
  }
}
