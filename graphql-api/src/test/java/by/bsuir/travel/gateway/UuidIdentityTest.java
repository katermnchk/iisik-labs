package by.bsuir.travel.gateway;

import static org.junit.jupiter.api.Assertions.*;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.gateway.mock.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class UuidIdentityTest {
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  private final MockData source = new MockData(mapper) {
    @Override public <T> List<T> list(String name, Class<T> type) {
      try {
        String json = mapper.writeValueAsString(super.list(name, type))
            .replace("00000000-", "aaaaaaaa-");
        return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, type));
      } catch (Exception e) { throw new IllegalStateException(e); }
    }
  };
  private String id(int n) { return String.format("AAAAAAAA-0000-0000-0000-%012d", n); }

  @Test void userLookupAcceptsEquivalentUuid() {
    assertEquals("aaaaaaaa-0000-0000-0000-000000000001", new MockUserClient(source).user(new GetUserRequest(id(1))).id());
  }
  @Test void bookingLookupAcceptsEquivalentUuid() {
    assertEquals("aaaaaaaa-0000-0000-0000-000000000601", new MockBookingClient(source).booking(new GetBookingRequest(id(601))).id());
  }
  @Test void tourLookupAcceptsEquivalentUuid() {
    assertEquals("aaaaaaaa-0000-0000-0000-000000000201", new MockTourClient(source).tour(new GetTourRequest(id(201), RoleCode.CLIENT)).id());
  }
  @Test void directionFilterAcceptsEquivalentUuid() {
    var request = new ListToursRequest(new TourFilter(id(101), null, null, null, null, null), null, new PageInput(0,20), null, null);
    assertEquals(1, new MockTourClient(source).tours(request).totalCount());
  }
  @Test void ownerFilterAcceptsEquivalentUuid() {
    assertEquals(3, new MockBookingClient(source).bookings(new ListBookingsRequest(id(1),new PageInput(0,20))).totalCount());
    assertEquals(1, new MockNotificationClient(source).notifications(new ListNotificationsRequest(id(1),new PageInput(0,20))).totalCount());
  }
  @Test void paymentBatchAcceptsEquivalentUuid() {
    assertEquals(2, new MockPaymentClient(source).payments(new ListPaymentsRequest(List.of(id(604)))).size());
  }
}
