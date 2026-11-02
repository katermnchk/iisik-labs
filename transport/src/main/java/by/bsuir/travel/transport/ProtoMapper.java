package by.bsuir.travel.transport;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;

/** Maps domain records to the generated, typed protobuf schema without JSON or numeric coercion. */
final class ProtoMapper {
  private ProtoMapper() {}

  static Message write(Object record, Message.Builder builder) {
    if (record == null) return builder.build();
    if (!record.getClass().isRecord())
      throw new IllegalArgumentException("Expected a domain record");
    for (var component : record.getClass().getRecordComponents()) {
      Object value;
      try {
        value = component.getAccessor().invoke(record);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
      if (value == null) continue;
      String name = fieldName(component.getName(), builder.getDescriptorForType().getName());
      var field = builder.getDescriptorForType().findFieldByName(name);
      if (field == null) {
        if (component.getName().equals("popularityAsOf"))
          continue; // only tour pages have this field
        throw new IllegalArgumentException("No protobuf field for " + component.getName());
      }
      if (component.getName().equals("basePriceMinor")) {
        builder.setField(
            field,
            builder
                .newBuilderForField(field)
                .setField(field.getMessageType().findFieldByName("amount_minor"), value)
                .setField(field.getMessageType().findFieldByName("currency"), "BYN")
                .build());
      } else if ((component.getName().equals("position") || component.getName().equals("phone"))
          && builder.getDescriptorForType().getName().equals("UpdateUserRequest")) {
        var nested = builder.newBuilderForField(field);
        nested.setField(nested.getDescriptorForType().getFields().get(0), value);
        builder.setField(field, nested.build());
      } else put(builder, field, value);
    }
    return builder.build();
  }

  static void put(Message.Builder builder, String name, Object value) {
    if (value != null)
      put(
          builder,
          Objects.requireNonNull(builder.getDescriptorForType().findFieldByName(name)),
          value);
  }

  private static void put(
      Message.Builder builder, Descriptors.FieldDescriptor field, Object value) {
    if (field.isRepeated()) {
      for (Object item : (List<?>) value)
        builder.addRepeatedField(field, scalar(builder, field, item));
    } else builder.setField(field, scalar(builder, field, value));
  }

  private static Object scalar(
      Message.Builder builder, Descriptors.FieldDescriptor field, Object value) {
    if (value instanceof Enum<?> e) {
      return field.getEnumType().getValues().stream()
          .filter(v -> v.getName().equals(enumName(field.getEnumType(), e.name())))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Unknown enum: " + e));
    }
    if (value instanceof OffsetDateTime date) {
      var instant = date.toInstant();
      return Timestamp.newBuilder()
          .setSeconds(instant.getEpochSecond())
          .setNanos(instant.getNano())
          .build();
    }
    if (value instanceof LocalDate date) {
      var nested = builder.newBuilderForField(field);
      put(nested, "year", date.getYear());
      put(nested, "month", date.getMonthValue());
      put(nested, "day", date.getDayOfMonth());
      return nested.build();
    }
    if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE)
      return write(value, builder.newBuilderForField(field));
    return value;
  }

  static <T> T read(Message message, Class<T> type) {
    return read(message, (Type) type);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static <T> T read(Message message, Type type) {
    Class<?> raw =
        type instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) type;
    if (!raw.isRecord()) throw new IllegalArgumentException("Expected a domain record: " + raw);
    var components = raw.getRecordComponents();
    Object[] args = new Object[components.length];
    Class<?>[] signature = new Class<?>[components.length];
    for (int i = 0; i < components.length; i++) {
      var component = components[i];
      signature[i] = component.getType();
      var field =
          message
              .getDescriptorForType()
              .findFieldByName(
                  fieldName(component.getName(), message.getDescriptorForType().getName()));
      if (field == null && component.getName().equals("popularityAsOf")) {
        args[i] = null;
        continue;
      }
      if (field == null)
        throw new IllegalArgumentException("No protobuf field for " + component.getName());
      if (field.hasPresence() && !message.hasField(field)) {
        args[i] = null;
        continue;
      }
      Object value = message.getField(field);
      Type componentType = component.getGenericType();
      if (componentType instanceof ParameterizedType listType
          && listType.getRawType() == List.class) {
        Type elementType = listType.getActualTypeArguments()[0];
        if (elementType instanceof TypeVariable<?> && type instanceof ParameterizedType p)
          elementType = p.getActualTypeArguments()[0];
        final Type element = elementType;
        args[i] = ((List<?>) value).stream().map(v -> decode(v, element)).toList();
      } else if (component.getName().equals("basePriceMinor")) {
        var money = (Message) value;
        args[i] = money.getField(money.getDescriptorForType().findFieldByName("amount_minor"));
      } else if ((component.getName().equals("position") || component.getName().equals("phone"))
          && message.getDescriptorForType().getName().equals("UpdateUserRequest")) {
        var nested = (Message) value;
        args[i] = nested.getField(nested.getDescriptorForType().getFields().get(0));
      } else args[i] = decode(value, componentType);
    }
    try {
      return (T) raw.getDeclaredConstructor(signature).newInstance(args);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof IllegalArgumentException invalid) throw invalid;
      throw new IllegalArgumentException("Invalid " + raw.getSimpleName(), e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object decode(Object value, Type type) {
    Class<?> raw =
        type instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) type;
    if (raw == OffsetDateTime.class) {
      var timestamp = (Message) value;
      long seconds =
          (Long) timestamp.getField(timestamp.getDescriptorForType().findFieldByName("seconds"));
      int nanos =
          (Integer) timestamp.getField(timestamp.getDescriptorForType().findFieldByName("nanos"));
      if (nanos < 0 || nanos > 999999999 || seconds < -62135596800L || seconds > 253402300799L)
        throw new IllegalArgumentException("Invalid protobuf timestamp");
      return OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneOffset.UTC);
    }
    if (raw == LocalDate.class) {
      var date = (Message) value;
      return LocalDate.of(
          (Integer) date.getField(date.getDescriptorForType().findFieldByName("year")),
          (Integer) date.getField(date.getDescriptorForType().findFieldByName("month")),
          (Integer) date.getField(date.getDescriptorForType().findFieldByName("day")));
    }
    if (raw.isEnum()) {
      String name = ((Descriptors.EnumValueDescriptor) value).getName();
      return Arrays.stream(raw.getEnumConstants())
          .filter(
              v ->
                  name.equals(
                      enumName(
                          ((Descriptors.EnumValueDescriptor) value).getType(),
                          ((Enum<?>) v).name())))
          .findFirst()
          .orElseThrow(
              () -> new IllegalArgumentException("Unspecified or unknown " + raw.getSimpleName()));
    }
    return value instanceof Message nested ? read(nested, type) : value;
  }

  private static String enumName(Descriptors.EnumDescriptor type, String value) {
    return type.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT)
        + "_"
        + value;
  }

  private static String fieldName(String name, String message) {
    if (name.equals("id") && message.equals("UpdateTourRequest")) return "tour_id";
    if (name.equals("id") && message.equals("UpdateScheduleRequest")) return "schedule_id";
    if (name.equals("basePriceMinor")) return "price";
    if (message.equals("UpdateUserRequest")) {
      if (name.equals("position")) return "employee_profile";
      if (name.equals("phone")) return "client_profile";
    }
    return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }
}
