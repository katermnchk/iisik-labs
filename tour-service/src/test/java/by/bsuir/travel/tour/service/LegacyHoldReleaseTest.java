package by.bsuir.travel.tour.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyHoldReleaseTest {
  @Test
  void retryingReleasedLegacyHoldDoesNotReturnSeatsAgain() {
    String id = "00000000-0000-0000-0000-000000000604";
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(contains("rpc_reservations"), eq(id))).thenReturn(List.of());
    when(jdbc.queryForList(contains("seat_holds"), eq(id)))
        .thenReturn(List.of(Map.of("schedule_id", id, "seats", 1, "status", "RELEASED")));
    assertTrue(new PersistentReservations(jdbc, null, new ObjectMapper()).release(id));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void cancellingMigratedDemoBookingReturnsItsHeldSeats() {
    String id = "00000000-0000-0000-0000-000000000604";
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(contains("rpc_reservations"), eq(id))).thenReturn(List.of());
    when(jdbc.queryForList(contains("seat_holds"), eq(id)))
        .thenReturn(List.of(Map.of("schedule_id", id, "seats", 1, "status", "HELD")));
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    assertTrue(new PersistentReservations(jdbc, null, new ObjectMapper()).release(id));
    verify(jdbc).update(contains("reserved_seats=reserved_seats-"), eq(1), eq(id), eq(1));
    verify(jdbc).update(contains("update seat_holds set status='RELEASED'"), eq(id));
  }
}
