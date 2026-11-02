package by.bsuir.travel.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayTest {
  @Autowired TestRestTemplate http;

  @Test
  void nullableDirectionsArgumentUsesDefaultActiveFilter() {
    JsonNode omitted = data("{directions{id}}", "demo-client");
    JsonNode explicitNull = data("{directions(activeOnly:null){id}}", "demo-client");
    assertEquals(omitted, explicitNull);
  }

  ResponseEntity<JsonNode> call(String query, String token) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) h.setBearerAuth(token);
    return http.exchange(
        "/graphql", HttpMethod.POST, new HttpEntity<>(Map.of("query", query), h), JsonNode.class);
  }

  JsonNode data(String q, String token) {
    JsonNode b = call(q, token).getBody();
    assertNotNull(b);
    assertFalse(b.has("errors"), b.toString());
    return b.get("data");
  }

  String id(int n) {
    return String.format("00000000-0000-0000-0000-%012d", n);
  }

  @Test
  void readsEmployeeProfile() {
    JsonNode me =
        data("{me{id firstName employeeProfile{userId position}}}", "demo-employee").get("me");
    assertEquals(id(3), me.path("employeeProfile").path("userId").asText());
    assertEquals("Менеджер туров", me.path("employeeProfile").path("position").asText());
  }

  @Test
  void nestedTourReturnsTwoAccommodationsAndTransport() {
    JsonNode t =
        data(
                "{tour(id:\""
                    + id(201)
                    + "\"){schedules{id accommodations{name} transports{kind}}}}",
                "demo-client")
            .get("tour");
    assertEquals(2, t.path("schedules").get(0).path("accommodations").size());
    assertEquals("BUS", t.path("schedules").get(0).path("transports").get(0).path("kind").asText());
  }

  @Test
  void filterDatesAndPriceReferToSameDeparture() {
    JsonNode c =
        data(
                "{tours(page:{page:0,size:20},filter:{from:\"2026-12-01\",to:\"2026-12-31\",maxPrice:\"60000\"}){totalCount"
                    + " items{id}}}",
                "demo-client")
            .get("tours");
    assertEquals(0, c.path("totalCount").asInt());
    assertEquals(0, c.path("items").size());
  }

  @Test
  void paginationPreservesTotalCount() {
    JsonNode c =
        data(
                "{tours(page:{page:0,size:1},sort:{field:PRICE,direction:ASC}){totalCount items{id}"
                    + " page{hasNext}}}",
                "demo-client")
            .get("tours");
    assertEquals(2, c.path("totalCount").asInt());
    assertEquals(1, c.path("items").size());
    assertTrue(c.path("page").path("hasNext").asBoolean());
  }

  @Test
  void onlyOwnBookingsAreReturned() {
    JsonNode c =
        data("{myBookings(page:{page:0,size:20}){totalCount items{id}}}", "demo-client")
            .get("myBookings");
    assertEquals(3, c.path("totalCount").asInt());
    for (JsonNode item : c.path("items")) assertNotEquals(id(602), item.path("id").asText());
  }

  @Test
  void bookingReturnsBothPaymentAttempts() {
    JsonNode b =
        data(
                "{booking(id:\"" + id(604) + "\"){payments{status amount{amountMinor}}}}",
                "demo-client")
            .get("booking");
    assertEquals(2, b.path("payments").size());
    assertEquals("FAILED", b.path("payments").get(0).path("status").asText());
    assertEquals("90000", b.path("payments").get(1).path("amount").path("amountMinor").asText());
  }

  @Test
  void clientCannotReadAnotherClientsBooking() {
    JsonNode b = call("{booking(id:\"" + id(602) + "\"){id}}", "demo-client").getBody();
    assertEquals("FORBIDDEN", b.path("errors").get(0).path("extensions").path("code").asText());
    assertTrue(b.path("data").path("booking").isNull());
  }

  @Test
  void unknownTourReturnsNullAndNotFound() {
    JsonNode b = call("{tour(id:\"" + id(999) + "\"){id}}", "demo-client").getBody();
    assertEquals("NOT_FOUND", b.path("errors").get(0).path("extensions").path("code").asText());
    assertTrue(b.path("data").path("tour").isNull());
  }

  @Test
  void unknownUuidAndInvalidPaginationAreRejected() {
    JsonNode b = call("{tour(id:\"bad\"){id}}", "demo-client").getBody();
    assertEquals("BAD_REQUEST", b.path("errors").get(0).path("extensions").path("code").asText());
    b = call("{tours(page:{page:0,size:101}){totalCount}}", "demo-client").getBody();
    assertEquals("BAD_REQUEST", b.path("errors").get(0).path("extensions").path("code").asText());
  }

  @Test
  void analyticsCountsSaleAndRefundOfOnePayment() {
    JsonNode a =
        data(
                "{salesAnalytics(filter:{from:\"2026-10-01T00:00:00Z\",to:\"2026-10-05T00:00:00Z\"}){grossSales{amountMinor}"
                    + " refunded{amountMinor} netSales{amountMinor}}}",
                "demo-admin")
            .get("salesAnalytics");
    assertEquals("280000", a.path("grossSales").path("amountMinor").asText());
    assertEquals("100000", a.path("refunded").path("amountMinor").asText());
    assertEquals("180000", a.path("netSales").path("amountMinor").asText());
  }

  @Test
  void clientCannotReadAnalytics() {
    JsonNode b =
        call(
                "{salesAnalytics(filter:{from:\"2026-10-01T00:00:00Z\",to:\"2026-10-05T00:00:00Z\"}){occupancy}}",
                "demo-client")
            .getBody();
    assertEquals("FORBIDDEN", b.path("errors").get(0).path("extensions").path("code").asText());
  }

  @Test
  void notificationsAreScopedToCurrentUser() {
    assertEquals(
        1,
        data("{notifications(page:{page:0,size:20}){totalCount}}", "demo-client")
            .path("notifications")
            .path("totalCount")
            .asInt());
  }

  @Test
  void authRejectsMissingUnknownAndBlockedTokens() {
    assertEquals(401, call("{me{id}}", null).getStatusCode().value());
    assertEquals(401, call("{me{id}}", "wrong").getStatusCode().value());
    assertEquals(403, call("{me{id}}", "demo-blocked").getStatusCode().value());
  }

  @Test
  void draftTourIsNotExposed() {
    JsonNode b = call("{tour(id:\"" + id(203) + "\"){id}}", "demo-client").getBody();
    assertEquals("NOT_FOUND", b.path("errors").get(0).path("extensions").path("code").asText());
  }

  @Test
  void directionsFilterInactiveRecords() {
    assertEquals(2, data("{directions{id}}", "demo-client").path("directions").size());
  }

  @Test
  void availabilityIncludesTemporaryHold() {
    JsonNode t =
        data("{tour(id:\"" + id(201) + "\"){schedules{id availableSeats}}}", "demo-client")
            .path("tour");
    assertEquals(7, t.path("schedules").get(0).path("availableSeats").asInt());
  }

  @Test
  void analyticsUsesUtcDaysForEquivalentOffsets() {
    JsonNode a =
        data(
                "{salesAnalytics(filter:{from:\"2026-09-30T21:00:00-03:00\",to:\"2026-10-04T21:00:00-03:00\"}){directions{directionId"
                    + " confirmedTourists}}}",
                "demo-admin")
            .path("salesAnalytics");
    assertEquals(id(101), a.path("directions").get(0).path("directionId").asText());
    assertEquals(2, a.path("directions").get(0).path("confirmedTourists").asInt());
    assertEquals(0, a.path("directions").get(1).path("confirmedTourists").asInt());
  }

  @Test
  void analyticsRejectsPartialDays() {
    JsonNode b =
        call(
                "{salesAnalytics(filter:{from:\"2026-10-01T12:00:00Z\",to:\"2026-10-05T00:00:00Z\"}){occupancy}}",
                "demo-admin")
            .getBody();
    assertEquals("BAD_REQUEST", b.path("errors").get(0).path("extensions").path("code").asText());
  }

  @Test
  void occupancyUsesWeightedCapacities() {
    JsonNode a =
        data(
                "{salesAnalytics(filter:{from:\"2026-12-01T00:00:00Z\",to:\"2027-01-01T00:00:00Z\"}){occupancy}}",
                "demo-admin")
            .path("salesAnalytics");
    assertEquals(0.4, a.path("occupancy").asDouble(), 0.00001);
  }

  @Test
  void popularitySortIncludesSnapshotTime() {
    JsonNode c =
        data(
                "{tours(page:{page:0,size:20},sort:{field:POPULARITY,direction:DESC}){items{id}"
                    + " popularityAsOf}}",
                "demo-client")
            .path("tours");
    assertEquals(id(202), c.path("items").get(0).path("id").asText());
    assertEquals("2026-10-05T00:00:00.000Z", c.path("popularityAsOf").asText());
  }
  @Test
  void nullableFieldFailurePreservesOtherRootFields() {
    JsonNode b = call("{me{id} tour(id:\"" + id(999) + "\"){id}}", "demo-client").getBody();
    assertEquals(id(1), b.path("data").path("me").path("id").asText());
    assertTrue(b.path("data").path("tour").isNull());
    assertEquals("NOT_FOUND", b.path("errors").get(0).path("extensions").path("code").asText());
  }

  @Test
  void batchPaymentsStayWithTheirOwnBooking() {
    JsonNode items = data("{myBookings(page:{page:0,size:20}){items{id payments{id bookingId}}}}", "demo-client").path("myBookings").path("items");
    int count = 0;
    for (JsonNode booking : items) {
      for (JsonNode payment : booking.path("payments")) {
        assertEquals(booking.path("id").asText(), payment.path("bookingId").asText());
        count++;
      }
      if (id(604).equals(booking.path("id").asText())) assertEquals(2, booking.path("payments").size());
    }
    assertEquals(4, count);
  }

  @Test
  void hugePageIsEmptyWithoutIntegerOverflow() {
    JsonNode c = data("{tours(page:{page:2147483647,size:100}){totalCount items{id} page{hasNext}}}", "demo-client").path("tours");
    assertEquals(2, c.path("totalCount").asInt());
    assertTrue(c.path("items").isEmpty());
    assertFalse(c.path("page").path("hasNext").asBoolean());
  }

  @Test
  void staffCanReadBookingOfAnotherClient() {
    for (String token : new String[]{"demo-employee", "demo-admin"}) {
      assertEquals(id(602), data("{booking(id:\"" + id(602) + "\"){id}}", token).path("booking").path("id").asText());
    }
  }

  @Test
  void scalarValidationReturnsGraphqlErrorWithoutServerFailure() {
    var response = call("{tours(page:{page:0,size:20},filter:{maxPrice:\"9223372036854775808\"}){totalCount}}", "demo-client");
    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getBody().has("errors"));
    assertEquals("ValidationError", response.getBody().path("errors").get(0).path("extensions").path("classification").asText());
  }
}
