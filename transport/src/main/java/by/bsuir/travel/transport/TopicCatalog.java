package by.bsuir.travel.transport;

import java.util.*;

public final class TopicCatalog {
  public static final String USER = "user.events.v1",
      TOUR = "tour.events.v1",
      BOOKING = "booking.events.v1",
      PAYMENT = "payment.events.v1";
  public static final int PARTITIONS = 3;
  public static final Map<String, String> EVENT_TOPICS =
      Map.of(
          "UserRegistered",
          USER,
          "UserUpdated",
          USER,
          "TourChanged",
          TOUR,
          "BookingStatusChanged",
          BOOKING,
          "PaymentSucceeded",
          PAYMENT);
  public static final Map<String, Set<String>> SUBSCRIPTIONS =
      Map.of(
          "booking",
          Set.of(PAYMENT),
          "analytics",
          Set.of(PAYMENT, TOUR),
          "notification",
          Set.of(USER, BOOKING, PAYMENT));

  private TopicCatalog() {}

  public static String topic(String eventType) {
    String t = EVENT_TOPICS.get(eventType);
    if (t == null) throw new IllegalArgumentException("Unknown event type " + eventType);
    return t;
  }

  public static String owner(String topic) {
    return switch (topic) {
      case USER -> "user-service";
      case TOUR -> "tour-service";
      case BOOKING -> "booking-service";
      case PAYMENT -> "payment-service";
      default -> throw new IllegalArgumentException("Unknown topic");
    };
  }

  public static String dlt(String source, String consumer) {
    if (!SUBSCRIPTIONS.getOrDefault(consumer, Set.of()).contains(source))
      throw new IllegalArgumentException("No such subscription");
    return source + "." + consumer + ".dlt";
  }

  public static Set<String> topics() {
    return Set.of(USER, TOUR, BOOKING, PAYMENT);
  }
}
