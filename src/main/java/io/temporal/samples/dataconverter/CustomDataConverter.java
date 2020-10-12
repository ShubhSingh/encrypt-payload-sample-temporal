package io.temporal.samples.dataconverter;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Defaults;
import io.temporal.api.common.v1.Payload;
import io.temporal.api.common.v1.Payloads;
import io.temporal.common.converter.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** DataConverter that delegates conversion to type specific PayloadConverter instance. */
public class CustomDataConverter implements DataConverter {

  private static final String METADATA_ENCODING_KEY = "encoding";

  private static final AtomicReference<DataConverter> defaultDataConverterInstance =
      new AtomicReference<>(
          // Order is important as the first converter that can convert the payload is used
          new CustomDataConverter(
              new NullPayloadConverter(),
              new ByteArrayPayloadConverter(),
              new ProtobufJsonPayloadConverter(),
              // Custom Jackson Json PayloadConverter
              new CustomJsonEncryptPayloadConverter()));

  private final Map<String, PayloadConverter> converterMap = new ConcurrentHashMap<>();

  private final List<PayloadConverter> converters = new ArrayList<>();

  static DataConverter getDefaultInstance() {
    return defaultDataConverterInstance.get();
  }

  /**
   * Override the global data converter default. Consider overriding data converter per client
   * instance (using {@link
   * io.temporal.client.WorkflowClientOptions.Builder#setDataConverter(DataConverter)} to avoid
   * potential conflicts.
   */
  public static void setDefaultDataConverter(DataConverter converter) {
    defaultDataConverterInstance.set(converter);
  }

  /**
   * Creates instance from ordered array of converters. When converting an object to payload the
   * array of converters is iterated from the beginning until one of the converters succesfully
   * converts the value.
   */
  public CustomDataConverter(PayloadConverter... converters) {
    for (PayloadConverter converter : converters) {
      this.converters.add(converter);
      this.converterMap.put(converter.getEncodingType(), converter);
    }
  }

  @Override
  public <T> Optional<Payload> toPayload(T value) {
    for (PayloadConverter converter : converters) {
      Optional<Payload> result = converter.toData(value);
      if (result.isPresent()) {
        return result;
      }
    }
    throw new IllegalArgumentException("Failure serializing " + value);
  }

  @Override
  public <T> T fromPayload(Payload payload, Class<T> valueClass, Type valueType) {
    try {
      String encoding = payload.getMetadataOrThrow(METADATA_ENCODING_KEY).toString(UTF_8);
      PayloadConverter converter = converterMap.get(encoding);
      if (converter == null) {
        throw new IllegalArgumentException("Unknown encoding: " + encoding);
      }
      return converter.fromData(payload, valueClass, valueType);
    } catch (DataConverterException e) {
      throw e;
    } catch (Exception e) {
      throw new DataConverterException(payload, valueClass, e);
    }
  }

  /**
   * When values is empty or it contains a single value and it is null then return empty blob. If a
   * single value do not wrap it into Json array. Exception stack traces are converted to a single
   * string stack trace to save space and make them more readable.
   *
   * @return serialized values
   */
  @Override
  public Optional<Payloads> toPayloads(Object... values) throws DataConverterException {
    if (values == null || values.length == 0) {
      return Optional.empty();
    }
    try {
      Payloads.Builder result = Payloads.newBuilder();
      for (Object value : values) {
        Optional<Payload> payload = toPayload(value);
        if (payload.isPresent()) {
          result.addPayloads(payload.get());
        } else {
          result.addPayloads(Payload.getDefaultInstance());
        }
      }
      return Optional.of(result.build());
    } catch (DataConverterException e) {
      throw e;
    } catch (Throwable e) {
      throw new DataConverterException(e);
    }
  }

  @Override
  public <T> T fromPayloads(
      int index, Optional<Payloads> content, Class<T> parameterType, Type genericParameterType)
      throws DataConverterException {
    if (!content.isPresent()) {
      return (T) Defaults.defaultValue((Class<?>) parameterType);
    }
    int count = content.get().getPayloadsCount();
    // To make adding arguments a backwards compatible change
    if (index >= count) {
      return (T) Defaults.defaultValue((Class<?>) parameterType);
    }
    return fromPayload(content.get().getPayloads(index), parameterType, genericParameterType);
  }
}