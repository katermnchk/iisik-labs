package by.bsuir.travel.tour.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import by.bsuir.travel.business.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.DomainException;
import by.bsuir.travel.contracts.Identity.ReserveRequest;
import by.bsuir.travel.contracts.Model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ReservationAmountTest {
  @Test
  @SuppressWarnings("unchecked")
  void unrepresentableTotalIsRejectedBeforeSeatsAreHeld() {
    String id = "00000000-0000-0000-0000-000000000301";
    var schedule =
        new TourSchedule(
            id,
            id,
            OffsetDateTime.now().plusDays(1),
            OffsetDateTime.now().plusDays(2),
            10,
            0,
            10,
            new Money(Long.MAX_VALUE, "BYN"),
            1,
            1,
            ScheduleStatus.OPEN,
            List.of(),
            List.of());
    var tour =
        new Tour(
            id,
            new Direction(id, "BY", "Minsk", "", true),
            "Tour",
            "",
            1,
            TourStatus.PUBLISHED,
            1,
            id,
            List.of(schedule));
    Store<Tour> store = mock(Store.class);
    when(store.all()).thenReturn(List.of(tour));
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    var reservations =
        new PersistentReservations(
            jdbc,
            new TourBusiness(store, List::of, Clock.systemUTC()),
            new ObjectMapper().findAndRegisterModules());
    var error =
        assertThrows(
            DomainException.class,
            () -> reservations.reserve(new ReserveRequest(id, new CreateBookingInput(id, 2, 1))));
    assertEquals("BAD_REQUEST", error.getCode());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }
}
