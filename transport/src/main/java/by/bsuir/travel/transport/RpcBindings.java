package by.bsuir.travel.transport;

import by.bsuir.travel.contracts.Clients.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.proto.v1.*;
import com.google.protobuf.Message;
import io.grpc.MethodDescriptor;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;

/** Explicit adapters bind each Java port to one named, generated protobuf RPC. */
final class RpcBindings {
  record Binding(
      Method javaMethod,
      MethodDescriptor<Message, Message> rpc,
      Function<Object[], Message> request,
      Function<Message, Object[]> arguments,
      Function<Object, Message> response,
      Function<Message, Object> result,
      boolean retryable) {}

  static final List<Binding> ALL = build();

  private RpcBindings() {}

  private static List<Binding> build() {
    List<Binding> bindings = new ArrayList<>();
    query(
        bindings,
        UserClient.class,
        "user",
        UserServiceGrpc.getGetUserMethod(),
        GetUserRequest.getDefaultInstance(),
        GetUserResponse.getDefaultInstance(),
        "user");
    query(
        bindings,
        UserClient.class,
        "principal",
        UserServiceGrpc.getGetPrincipalMethod(),
        GetPrincipalRequest.getDefaultInstance(),
        GetPrincipalResponse.getDefaultInstance(),
        "principal");
    query(
        bindings,
        Directory.class,
        "registerProfile",
        UserServiceGrpc.getRegisterProfileMethod(),
        RegisterProfileRequest.getDefaultInstance(),
        GetUserResponse.getDefaultInstance(),
        "user");
    query(
        bindings,
        TourClient.class,
        "tour",
        TourServiceGrpc.getGetTourMethod(),
        GetTourRequest.getDefaultInstance(),
        GetTourResponse.getDefaultInstance(),
        "tour");
    query(
        bindings,
        TourClient.class,
        "tours",
        TourServiceGrpc.getListToursMethod(),
        ListToursRequest.getDefaultInstance(),
        TourConnection.getDefaultInstance(),
        null);
    query(
        bindings,
        TourClient.class,
        "directions",
        TourServiceGrpc.getListDirectionsMethod(),
        ListDirectionsRequest.getDefaultInstance(),
        ListDirectionsResponse.getDefaultInstance(),
        "directions");
    query(
        bindings,
        BookingClient.class,
        "booking",
        BookingServiceGrpc.getGetBookingMethod(),
        GetBookingRequest.getDefaultInstance(),
        BookingResponse.getDefaultInstance(),
        "booking");
    query(
        bindings,
        BookingClient.class,
        "bookings",
        BookingServiceGrpc.getListClientBookingsMethod(),
        ListBookingsRequest.getDefaultInstance(),
        BookingConnection.getDefaultInstance(),
        null);
    query(
        bindings,
        PaymentClient.class,
        "payments",
        PaymentServiceGrpc.getListBookingPaymentsMethod(),
        ListPaymentsRequest.getDefaultInstance(),
        ListPaymentsResponse.getDefaultInstance(),
        "payments");
    query(
        bindings,
        NotificationClient.class,
        "notifications",
        NotificationServiceGrpc.getListUserNotificationsMethod(),
        ListNotificationsRequest.getDefaultInstance(),
        NotificationConnection.getDefaultInstance(),
        null);
    query(
        bindings,
        AnalyticsClient.class,
        "analytics",
        AnalyticsServiceGrpc.getGetSalesAnalyticsMethod(),
        GetSalesAnalyticsRequest.getDefaultInstance(),
        SalesAnalyticsResponse.getDefaultInstance(),
        "analytics");
    query(
        bindings,
        AnalyticsClient.class,
        "popularity",
        AnalyticsServiceGrpc.getGetPopularityMethod(),
        GetPopularityRequest.getDefaultInstance(),
        GetPopularityResponse.getDefaultInstance(),
        "directions");
    command(
        bindings,
        TourCommands.class,
        "createTour",
        TourServiceGrpc.getCreateTourMethod(),
        CreateTourRequest.getDefaultInstance(),
        GetTourResponse.getDefaultInstance(),
        "tour");
    command(
        bindings,
        TourCommands.class,
        "updateTour",
        TourServiceGrpc.getUpdateTourMethod(),
        UpdateTourRequest.getDefaultInstance(),
        GetTourResponse.getDefaultInstance(),
        "tour");
    command(
        bindings,
        TourCommands.class,
        "createSchedule",
        TourServiceGrpc.getCreateScheduleMethod(),
        CreateScheduleRequest.getDefaultInstance(),
        ScheduleResponse.getDefaultInstance(),
        "schedule");
    command(
        bindings,
        TourCommands.class,
        "updateSchedule",
        TourServiceGrpc.getUpdateScheduleMethod(),
        UpdateScheduleRequest.getDefaultInstance(),
        ScheduleResponse.getDefaultInstance(),
        "schedule");
    command(
        bindings,
        BookingCommands.class,
        "createBooking",
        BookingServiceGrpc.getCreateBookingMethod(),
        CreateBookingRequest.getDefaultInstance(),
        BookingResponse.getDefaultInstance(),
        "booking");
    command(
        bindings,
        BookingCommands.class,
        "cancelBooking",
        BookingServiceGrpc.getCancelBookingMethod(),
        CancelBookingRequest.getDefaultInstance(),
        BookingResponse.getDefaultInstance(),
        "booking");
    command(
        bindings,
        BookingCommands.class,
        "changeBookingStatus",
        BookingServiceGrpc.getChangeBookingStatusMethod(),
        ChangeBookingStatusRequest.getDefaultInstance(),
        BookingResponse.getDefaultInstance(),
        "booking");
    command(
        bindings,
        PaymentCommands.class,
        "createPayment",
        PaymentServiceGrpc.getCreatePaymentMethod(),
        CreatePaymentRequest.getDefaultInstance(),
        PaymentResponse.getDefaultInstance(),
        "payment");
    command(
        bindings,
        UserCommands.class,
        "updateUser",
        UserServiceGrpc.getUpdateUserMethod(),
        UpdateUserRequest.getDefaultInstance(),
        GetUserResponse.getDefaultInstance(),
        "user");
    query(
        bindings,
        PaymentAdmission.class,
        "claimPayment",
        BookingServiceGrpc.getClaimPaymentMethod(),
        PaymentClaimRequest.getDefaultInstance(),
        BooleanResponse.getDefaultInstance(),
        "value");
    string(
        bindings,
        ScheduleDirectory.class,
        "schedule",
        TourServiceGrpc.getGetScheduleMethod(),
        GetScheduleRequest.getDefaultInstance(),
        "id",
        ScheduleResponse.getDefaultInstance(),
        "schedule",
        true);
    string(
        bindings,
        Reservations.class,
        "release",
        TourServiceGrpc.getReleaseReservationMethod(),
        ReleaseReservationRequest.getDefaultInstance(),
        "booking_id",
        ReservationResponse.getDefaultInstance(),
        "status",
        true);
    string(
        bindings,
        Settlement.class,
        "confirmPayment",
        PaymentServiceGrpc.getConfirmPaymentMethod(),
        ConfirmPaymentRequest.getDefaultInstance(),
        "payment_id",
        PaymentResponse.getDefaultInstance(),
        "payment",
        false);
    string(
        bindings,
        Settlement.class,
        "expirePayment",
        PaymentServiceGrpc.getExpirePaymentMethod(),
        BookingIdRequest.getDefaultInstance(),
        "booking_id",
        BooleanResponse.getDefaultInstance(),
        "value",
        true);
    string(
        bindings,
        PaymentAdmission.class,
        "expireBooking",
        BookingServiceGrpc.getExpireBookingMethod(),
        BookingIdRequest.getDefaultInstance(),
        "booking_id",
        BooleanResponse.getDefaultInstance(),
        "value",
        true);
    string(
        bindings,
        PaymentAdmission.class,
        "releaseOrphanReservation",
        BookingServiceGrpc.getReleaseOrphanReservationMethod(),
        BookingIdRequest.getDefaultInstance(),
        "booking_id",
        BooleanResponse.getDefaultInstance(),
        "value",
        true);
    var reserve = method(Reservations.class, "reserve");
    bindings.add(
        binding(
            reserve,
            TourServiceGrpc.getReserveSeatsMethod(),
            args -> {
              var input = (ReserveRequest) args[0];
              return ReserveSeatsRequest.newBuilder()
                  .setBookingId(input.bookingId())
                  .setScheduleId(input.input().scheduleId())
                  .setSeats(input.input().travelerCount())
                  .setExpectedPriceVersion(input.input().expectedPriceVersion())
                  .build();
            },
            request -> {
              var r = (ReserveSeatsRequest) request;
              return new Object[] {
                new ReserveRequest(
                    r.getBookingId(),
                    new CreateBookingInput(
                        r.getScheduleId(), r.getSeats(), r.getExpectedPriceVersion()))
              };
            },
            RpcBindings::reservationResponse,
            response -> ProtoMapper.read(response, Reservation.class),
            true));
    for (String name : List.of("filtered", "summary")) {
      var java = method(BookingQueries.class, name);
      bindings.add(
          binding(
              java,
              name.equals("filtered")
                  ? BookingServiceGrpc.getFilterBookingsMethod()
                  : BookingServiceGrpc.getSummarizeBookingsMethod(),
              args -> {
                var builder = FilterBookingsRequest.newBuilder();
                ProtoMapper.put(builder, "actor", args[0]);
                ProtoMapper.put(builder, "filter", args[1]);
                if (args.length == 3) ProtoMapper.put(builder, "sort", args[2]);
                return builder.build();
              },
              request -> {
                var types = java.getGenericParameterTypes();
                Object[] args = new Object[types.length];
                for (int i = 0; i < args.length; i++)
                  args[i] = readField(request, List.of("actor", "filter", "sort").get(i), types[i]);
                return args;
              },
              result -> {
                if (name.equals("summary"))
                  return ProtoMapper.write(
                      result, by.bsuir.travel.proto.v1.BookingSummary.newBuilder());
                var builder = BookingConnection.newBuilder();
                ProtoMapper.put(builder, "items", result);
                builder.setTotalCount(((List<?>) result).size());
                return builder.build();
              },
              response ->
                  name.equals("summary")
                      ? ProtoMapper.read(response, java.getGenericReturnType())
                      : readResult(response, "items", java.getGenericReturnType()),
              true));
    }
    return List.copyOf(bindings);
  }

  private static void query(
      List<Binding> bindings,
      Class<?> api,
      String name,
      MethodDescriptor<?, ?> rpc,
      Message request,
      Message response,
      String field) {
    var java = method(api, name);
    bindings.add(
        binding(
            java,
            rpc,
            args -> ProtoMapper.write(args[0], request.newBuilderForType()),
            message -> new Object[] {ProtoMapper.read(message, java.getGenericParameterTypes()[0])},
            result -> response(result, response, field),
            message -> readResult(message, field, java.getGenericReturnType()),
            true));
  }

  private static void command(
      List<Binding> bindings,
      Class<?> api,
      String name,
      MethodDescriptor<?, ?> rpc,
      Message request,
      Message response,
      String field) {
    var java = method(api, name);
    bindings.add(
        binding(
            java,
            rpc,
            args -> {
              var builder = ProtoMapper.write(args[0], request.newBuilderForType()).toBuilder();
              ProtoMapper.put(builder, "actor", args[args.length - 1]);
              if (args.length == 3) ProtoMapper.put(builder, "idempotency_key", args[1]);
              return builder.build();
            },
            message -> {
              var types = java.getGenericParameterTypes();
              Object[] args = new Object[types.length];
              args[0] = ProtoMapper.read(message, types[0]);
              args[args.length - 1] = readField(message, "actor", types[types.length - 1]);
              if (args.length == 3) args[1] = readField(message, "idempotency_key", String.class);
              return args;
            },
            result -> response(result, response, field),
            message -> readResult(message, field, java.getGenericReturnType()),
            false));
  }

  private static void string(
      List<Binding> bindings,
      Class<?> api,
      String name,
      MethodDescriptor<?, ?> rpc,
      Message request,
      String inputField,
      Message response,
      String outputField,
      boolean retryable) {
    var java = method(api, name);
    bindings.add(
        binding(
            java,
            rpc,
            args -> {
              var builder = request.newBuilderForType();
              ProtoMapper.put(builder, inputField, args[0]);
              if (args.length == 2) ProtoMapper.put(builder, "actor", args[1]);
              return builder.build();
            },
            message -> {
              Object id = readField(message, inputField, String.class);
              return java.getParameterCount() == 1
                  ? new Object[] {id}
                  : new Object[] {
                    id, readField(message, "actor", java.getGenericParameterTypes()[1])
                  };
            },
            result ->
                response(
                    name.equals("release")
                        ? (Boolean.TRUE.equals(result) ? "RELEASED" : "HELD")
                        : result,
                    response,
                    outputField),
            message ->
                name.equals("release")
                    ? readRelease(message)
                    : readResult(message, outputField, java.getGenericReturnType()),
            retryable));
  }

  private static Message reservationResponse(Object result) {
    var reservation = (Reservation) result;
    var builder = ProtoMapper.write(reservation, ReserveSeatsResponse.newBuilder()).toBuilder();
    var schedule = reservation.schedule();
    var tour = reservation.tour();
    ProtoMapper.put(builder, "schedule_id", schedule.id());
    ProtoMapper.put(builder, "unit_price", schedule.price());
    ProtoMapper.put(
        builder,
        "total",
        new by.bsuir.travel.contracts.Model.Money(
            Math.multiplyExact(schedule.price().amountMinor(), reservation.seats()),
            schedule.price().currency()));
    ProtoMapper.put(builder, "price_version", schedule.priceVersion());
    ProtoMapper.put(builder, "tour_id", tour.id());
    ProtoMapper.put(builder, "direction_id", tour.direction().id());
    ProtoMapper.put(builder, "tour_title", tour.title());
    ProtoMapper.put(
        builder, "direction_name", tour.direction().country() + ", " + tour.direction().city());
    ProtoMapper.put(builder, "starts_at", schedule.startsAt());
    return builder.build();
  }

  private static Boolean readRelease(Message message) {
    var value = ((ReservationResponse) message).getStatus();
    if (value.equals("RELEASED")) return true;
    if (value.equals("HELD")) return false;
    throw new IllegalStateException("Unknown reservation release status");
  }

  private static Message response(Object result, Message template, String field) {
    if (field == null) return ProtoMapper.write(result, template.newBuilderForType());
    var builder = template.newBuilderForType();
    ProtoMapper.put(builder, field, result);
    return builder.build();
  }

  private static Object readResult(Message message, String field, Type type) {
    if (field == null) return ProtoMapper.read(message, type);
    var descriptor = message.getDescriptorForType().findFieldByName(field);
    Object value = message.getField(descriptor);
    if (descriptor.isRepeated()) {
      Type element = ((ParameterizedType) type).getActualTypeArguments()[0];
      return ((List<?>) value)
          .stream()
              .map(v -> v instanceof Message nested ? ProtoMapper.read(nested, element) : v)
              .toList();
    }
    if (descriptor.hasPresence() && !message.hasField(descriptor))
      throw new IllegalStateException("Missing RPC result: " + field);
    return value instanceof Message nested ? ProtoMapper.read(nested, type) : value;
  }

  private static Object readField(Message message, String name, Type type) {
    var field = message.getDescriptorForType().findFieldByName(name);
    if (field.hasPresence() && !message.hasField(field)) return null;
    Object value = message.getField(field);
    return value instanceof Message nested ? ProtoMapper.read(nested, type) : value;
  }

  private static Method method(Class<?> api, String name) {
    return Arrays.stream(api.getMethods())
        .filter(m -> m.getName().equals(name))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException("Missing port operation " + api.getName() + "." + name));
  }

  @SuppressWarnings("unchecked")
  private static Binding binding(
      Method java,
      MethodDescriptor<?, ?> rpc,
      Function<Object[], Message> request,
      Function<Message, Object[]> arguments,
      Function<Object, Message> response,
      Function<Message, Object> result,
      boolean retryable) {
    return new Binding(
        java,
        (MethodDescriptor<Message, Message>) rpc,
        request,
        arguments,
        response,
        result,
        retryable);
  }
}
