package by.bsuir.travel.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import graphql.parser.Parser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostmanFlowTest {
  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper mapper;

  @Test
  void shippedCollectionRunsInItsDocumentedOrder() throws Exception {
    var collection =
        mapper.readTree(Files.readString(Path.of("../examples/lab4.postman_collection.json")));
    var variables = new HashMap<String, String>();
    variables.put("bookingId", "");
    variables.put("tourId", "");
    variables.put("tourVersion", "2");
    int requests = run(collection.path("item"), variables);
    assertEquals(12, requests);
  }

  int run(JsonNode items, Map<String, String> variables) throws Exception {
    int count = 0;
    for (var item : items) {
      if (item.has("item")) {
        count += run(item.path("item"), variables);
        continue;
      }
      var request = item.path("request");
      String raw = request.path("body").path("raw").asText();
      for (var e : variables.entrySet()) raw = raw.replace("{{" + e.getKey() + "}}", e.getValue());
      var payload = mapper.readTree(raw);
      new Parser().parseDocument(payload.path("query").asText());
      var headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      for (var h : request.path("header"))
        if (h.path("key").asText().equals("Authorization"))
          headers.set("Authorization", h.path("value").asText());
      var response =
          http.exchange(
              "/graphql", HttpMethod.POST, new HttpEntity<>(payload, headers), JsonNode.class);
      assertEquals(200, response.getStatusCode().value(), item.path("name").asText());
      var body = response.getBody();
      assertNotNull(body);
      assertFalse(body.has("errors"), item.path("name").asText() + ": " + body);
      var data = body.path("data");
      if (data.has("createBooking")) {
        assertEquals("PENDING_PAYMENT", data.path("createBooking").path("status").asText());
        variables.put("bookingId", data.path("createBooking").path("id").asText());
      }
      if (data.has("createSchedule")) {
        variables.put("scheduleId", data.path("createSchedule").path("id").asText());
        variables.put("scheduleVersion", data.path("createSchedule").path("version").asText());
      }
      if (data.has("updateSchedule"))
        assertEquals("2", data.path("updateSchedule").path("priceVersion").asText());
      if (data.has("createTour"))
        variables.put("tourId", data.path("createTour").path("id").asText());
      if (data.has("tour")) {
        assertEquals("DRAFT", data.path("tour").path("status").asText());
        variables.put("tourVersion", data.path("tour").path("version").asText());
      }
      if (data.has("updateTour"))
        assertEquals("PUBLISHED", data.path("updateTour").path("status").asText());
      if (data.has("cancelBooking"))
        assertEquals("CANCELLED", data.path("cancelBooking").path("status").asText());
      if (data.has("createPayment"))
        assertEquals("PENDING", data.path("createPayment").path("status").asText());
      if (data.has("changeBookingStatus"))
        assertEquals("COMPLETED", data.path("changeBookingStatus").path("status").asText());
      count++;
    }
    return count;
  }
}
