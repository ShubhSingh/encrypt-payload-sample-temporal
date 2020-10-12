package io.temporal.samples.dataconverter;

import com.codingrodent.jackson.crypto.CryptoModule;
import com.codingrodent.jackson.crypto.EncryptionService;
import com.codingrodent.jackson.crypto.PasswordCryptoContext;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.converter.DataConverterException;
import io.temporal.common.converter.PayloadConverter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomJsonEncryptPayloadConverter implements PayloadConverter {

  private static final Logger log =
      LoggerFactory.getLogger(CustomJsonEncryptPayloadConverter.class);
  private final ObjectMapper mapper;
  private static final String METADATA_ENCODING_JSON_NAME = "json/plain";
  private static final String METADATA_ENCODING_KEY = "encoding";
  private static final ByteString METADATA_ENCODING_JSON;

  static {
    METADATA_ENCODING_JSON = ByteString.copyFrom("json/plain", StandardCharsets.UTF_8);
  }

  public CustomJsonEncryptPayloadConverter() {
    mapper = new ObjectMapper();
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    mapper.registerModule(new JavaTimeModule());
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    // Enable encryption using jackson crypto
    // TODO: Find a way to rotate SecretKey and try 3 different keys
    EncryptionService encryptionService =
        new EncryptionService(mapper, new PasswordCryptoContext("SecretPass1"));
    mapper.registerModule(new CryptoModule().addEncryptionService(encryptionService));
  }

  public CustomJsonEncryptPayloadConverter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public String getEncodingType() {
    return METADATA_ENCODING_JSON_NAME;
  }

  @Override
  public Optional<Payload> toData(Object value) throws DataConverterException {
    try {
      log.info("Convert to byte[] the object: {}", value);
      byte[] serialized = mapper.writeValueAsBytes(value);
      return Optional.of(
          Payload.newBuilder()
              .putMetadata(METADATA_ENCODING_KEY, METADATA_ENCODING_JSON)
              .setData(ByteString.copyFrom(serialized))
              .build());

    } catch (JsonProcessingException e) {
      throw new DataConverterException(e);
    }
  }

  @Override
  public <T> T fromData(Payload content, Class<T> valueClass, Type valueType)
      throws DataConverterException {
    ByteString data = content.getData();
    if (data.isEmpty()) {
      return null;
    }
    try {
      @SuppressWarnings("deprecation")
      JavaType reference = mapper.getTypeFactory().constructType(valueType, valueClass);
      T t = mapper.readValue(content.getData().toByteArray(), reference);
      log.info("Convert back to object: {}", t);
      return t;
    } catch (IOException e) {
      throw new DataConverterException(e);
    }
  }
}
