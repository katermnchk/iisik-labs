package by.bsuir.travel.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MutationTest {
  @Autowired
  TestRestTemplate http;

  ResponseEntity<JsonNode> call(String q, String token) {
    var h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.setBearerAuth(token);
    return http.exchange(
        "/graphql",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("query", q), h),
        JsonNode.class);
  }

  JsonNode data(String q, String token) {
    var b = call(q, token).getBody();
    assertNotNull(b);
    assertFalse(b.has("errors"), b.toString());
    return b.get("data");
  }

  String id(int n) {
    return String.format("00000000-0000-0000-0000-%012d", n);
  }

  @Test
  void staffCanReadDraftAndCurrentVersionAfterAddingSchedule() {
    var t =
        data(
                "mutation{createTour(input:{directionId:\""
                    + id(101)
                    + "\",title:\"Новый тур\",description:\"Описание\",durationDays:2}){id"
                    + " version}}",
                "demo-employee")
            .path("createTour");
    String tid = t.path("id").asText();
    data(
        "mutation{createSchedule(input:{tourId:\""
            + tid
            + "\",startsAt:\"2026-12-10T06:00:00Z\",endsAt:\"2026-12-12T06:00:00Z\",capacity:10,basePriceMinor:\"90000\"}){id}}",
        "demo-employee");
    var draft = data("{tour(id:\"" + tid + "\"){status version}}", "demo-employee").path("tour");
    assertEquals("DRAFT", draft.path("status").asText());
    assertEquals("2", draft.path("version").asText());
    assertEquals("NOT_FOUND", error("{tour(id:\"" + tid + "\"){id}}", "demo-client"));
  }

  String create(int count, String key) {
    return "mutation{createBooking(input:{scheduleId:\""
        + id(301)
        + "\",travelerCount:"
        + count
        + ",expectedPriceVersion:\"1\"},idempotencyKey:\""
        + key
        + "\"){id status version total{amountMinor}}}";
  }

  String error(String q, String token) {
    return call(q, token).getBody().path("errors").get(0).path("extensions").path("code").asText();
  }

  @Test
  void bookingIsVisibleAndCancellationReleasesSeats() {
    var b = data(create(2, "book-1"), "demo-client").get("createBooking");
    assertEquals("PENDING_PAYMENT", b.path("status").asText());
    assertEquals("180000", b.path("total").path("amountMinor").asText());
    String bid = b.path("id").asText();
    assertEquals(
        "PENDING_PAYMENT",
        data("{booking(id:\"" + bid + "\"){status}}", "demo-client")
            .path("booking")
            .path("status")
            .asText());
    assertEquals(
        5,
        data("{tour(id:\"" + id(201) + "\"){schedules{id availableSeats}}}", "demo-client")
            .path("tour")
            .path("schedules")
            .get(0)
            .path("availableSeats")
            .asInt());
    assertEquals(
        "CANCELLED",
        data(
                "mutation{cancelBooking(input:{bookingId:\""
                    + bid
                    + "\",reason:\"Планы изменились\"},idempotencyKey:\"cancel-1\"){status}}",
                "demo-client")
            .path("cancelBooking")
            .path("status")
            .asText());
    assertEquals(
        7,
        data("{tour(id:\"" + id(201) + "\"){schedules{availableSeats}}}", "demo-client")
            .path("tour")
            .path("schedules")
            .get(0)
            .path("availableSeats")
            .asInt());
  }

  @Test
  void retriesReturnSameBookingAndChangedPayloadConflicts() {
    String a = data(create(2, "same-key"), "demo-client").path("createBooking").path("id").asText();
    assertEquals(
        a, data(create(2, "same-key"), "demo-client").path("createBooking").path("id").asText());
    assertEquals("CONFLICT", error(create(3, "same-key"), "demo-client"));
  }

  @Test
  void rejectsCapacityAndInvalidCountWithoutChangingSeats() {
    assertEquals("INSUFFICIENT_SEATS", error(create(8, "too-many"), "demo-client"));
    assertEquals("BAD_REQUEST", error(create(0, "zero"), "demo-client"));
  }

  @Test
  void paymentRetryIsIdempotentAndPendingPaymentPreventsCancellation() {
    String bid =
        data(create(1, "pay-book"), "demo-client").path("createBooking").path("id").asText();
    String q =
        "mutation{createPayment(input:{bookingId:\""
            + bid
            + "\"},idempotencyKey:\"pay-1\"){id status}}";
    String pid = data(q, "demo-client").path("createPayment").path("id").asText();
    assertEquals(pid, data(q, "demo-client").path("createPayment").path("id").asText());
    assertEquals(
        "CONFLICT",
        error(
            "mutation{cancelBooking(input:{bookingId:\""
                + bid
                + "\",reason:\"cancel\"},idempotencyKey:\"cancel-pay\"){status}}",
            "demo-client"));
  }

  @Test
  void clientCannotCreateTour() {
    assertEquals(
        "FORBIDDEN",
        error(
            "mutation{createTour(input:{directionId:\""
                + id(101)
                + "\",title:\"Тур\",description:\"Описание\",durationDays:2}){id}}",
            "demo-client"));
  }

  @Test
  void administratorCreatesEmployeeProfile() {
    var u =
        data(
                "mutation{updateUser(input:{userId:\""
                    + id(2)
                    + "\",role:EMPLOYEE,position:\"Менеджер\"}){role{code}"
                    + " employeeProfile{position}}}",
                "demo-admin")
            .path("updateUser");
    assertEquals("EMPLOYEE", u.path("role").path("code").asText());
    assertEquals("Менеджер", u.path("employeeProfile").path("position").asText());
  }

  @Test
  void startedConfirmedBookingCanBeCompletedFromCleanFixture() {
    var result =
        data(
                "mutation{changeBookingStatus(input:{bookingId:\""
                    + id(606)
                    + "\",targetStatus:COMPLETED,reason:\"Поездка"
                    + " завершена\",expectedVersion:\"1\"}){status version}}",
                "demo-employee")
            .path("changeBookingStatus");
    assertEquals("COMPLETED", result.path("status").asText());
    assertEquals("2", result.path("version").asText());
    assertEquals(
        "COMPLETED",
        data("{booking(id:\"" + id(606) + "\"){status}}", "demo-client2")
            .path("booking")
            .path("status")
            .asText());
  }

  @Test
  void cleanFixtureHasPayableBookingForSecondClient() {
    String q =
        "mutation{createPayment(input:{bookingId:\""
            + id(607)
            + "\"},idempotencyKey:\"fixture-pay\"){id status}}";
    var payment = data(q, "demo-client2").path("createPayment");
    assertEquals("PENDING", payment.path("status").asText());
    assertEquals(
        payment.path("id").asText(),
        data(q, "demo-client2").path("createPayment").path("id").asText());
  }
}
