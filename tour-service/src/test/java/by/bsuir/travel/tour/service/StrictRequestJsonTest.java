package by.bsuir.travel.tour.service;

import static org.junit.jupiter.api.Assertions.*;

import by.bsuir.travel.contracts.Commands.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class StrictRequestJsonTest {
  ObjectMapper mapper = mapper();

  ObjectMapper mapper() {
    var b = new Jackson2ObjectMapperBuilder();
    new BusinessConfiguration().strictJson().customize(b);
    return b.build();
  }

  String base =
      "{\"tourId\":\"00000000-0000-0000-0000-000000000201\",\"startsAt\":\"2026-12-10T06:00:00Z\",\"endsAt\":\"2026-12-12T06:00:00Z\",\"capacity\":10";

  @Test
  void missingPriceIsRejected() {
    assertThrows(
        JsonProcessingException.class,
        () -> mapper.readValue(base + "}", CreateScheduleInput.class));
  }

  @Test
  void nullPriceIsRejected() {
    assertThrows(
        JsonProcessingException.class,
        () -> mapper.readValue(base + ",\"basePriceMinor\":null}", CreateScheduleInput.class));
  }

  @Test
  void zeroPriceIsValid() throws Exception {
    assertEquals(
        0,
        mapper
            .readValue(base + ",\"basePriceMinor\":0}", CreateScheduleInput.class)
            .basePriceMinor());
  }

  @Test
  void missingExpectedVersionIsRejected() {
    assertThrows(
        JsonProcessingException.class,
        () ->
            mapper.readValue(
                "{\"id\":\"00000000-0000-0000-0000-000000000201\"}", UpdateTourInput.class));
  }

  @Test
  void optionalPatchFieldsRemainOptional() throws Exception {
    var v =
        mapper.readValue(
            "{\"id\":\"00000000-0000-0000-0000-000000000201\",\"expectedVersion\":1}",
            UpdateTourInput.class);
    assertNull(v.title());
    assertEquals(1, v.expectedVersion());
  }

  @Test
  void fractionalTravelerCountIsRejected() {
    assertThrows(
        JsonProcessingException.class,
        () ->
            mapper.readValue(
                "{\"scheduleId\":\"00000000-0000-0000-0000-000000000301\",\"travelerCount\":1.9,\"expectedPriceVersion\":1}",
                CreateBookingInput.class));
  }

  @Test
  void fractionalVersionIsRejected() {
    assertThrows(
        JsonProcessingException.class,
        () ->
            mapper.readValue(
                "{\"id\":\"00000000-0000-0000-0000-000000000201\",\"expectedVersion\":1.9}",
                UpdateTourInput.class));
  }
}
