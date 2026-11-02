package by.bsuir.travel.booking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import by.bsuir.travel.contracts.Identity.Reservations;
import by.bsuir.travel.contracts.Model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ExpiryAdmissionTest {
  final String id = "00000000-0000-0000-0000-000000000601";
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final BookingReadService read = mock(BookingReadService.class);
  final Reservations seats = mock(Reservations.class);
  final PaymentAdmissionService service =
      new PaymentAdmissionService(jdbc, read, seats, new ObjectMapper().findAndRegisterModules());

  Booking booking(BookingStatus status, OffsetDateTime deadline) {
    var now = OffsetDateTime.now();
    return new Booking(
        id,
        id,
        id,
        id,
        "Tour",
        "Direction",
        now.plusDays(1),
        1,
        new Money(100, "BYN"),
        new Money(100, "BYN"),
        status,
        deadline,
        now.minusHours(1),
        1,
        List.of());
  }

  @Test
  void settlementCommittedBeforeConsumerRunsCannotBeExpiredThroughConfirmedState() {
    when(read.booking(any())).thenReturn(booking(BookingStatus.CONFIRMED, null));
    assertFalse(service.expireBooking(id));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void terminalExpiryDecisionCanBeRetriedAfterPaymentTransactionFailure() {
    when(read.booking(any())).thenReturn(booking(BookingStatus.CANCELLING, null));
    assertTrue(service.expireBooking(id));
  }

  @Test
  void pendingHoldBeforeDeadlineRemainsPayable() {
    when(read.booking(any()))
        .thenReturn(booking(BookingStatus.PENDING_PAYMENT, OffsetDateTime.now().plusMinutes(5)));
    assertFalse(service.expireBooking(id));
  }

  @Test
  void expiredHoldStartsRecoverableCancellation() {
    when(read.booking(any()))
        .thenReturn(booking(BookingStatus.PENDING_PAYMENT, OffsetDateTime.now().minusMinutes(1)));
    // Direct unit invocation has no Spring interceptor; emulate its transaction context.
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertTrue(service.expireBooking(id));
    } finally {
      TransactionSynchronizationManager.clear();
    }
    verify(jdbc).update(contains("status='CANCELLING'"), eq(id));
  }

  @Test
  void orphanReconciliationKeepsACommittedBookingReservation() {
    when(jdbc.queryForList(anyString(), eq(id))).thenReturn(List.of(Map.of("id", id)));
    assertFalse(service.releaseOrphanReservation(id));
    verifyNoInteractions(seats);
  }

  @Test
  void orphanReconciliationReleasesOnlyWhenWriterProtectedCheckIsEmpty() {
    when(jdbc.queryForList(anyString(), eq(id))).thenReturn(List.of());
    when(seats.release(id)).thenReturn(true);
    assertTrue(service.releaseOrphanReservation(id));
    var order = inOrder(jdbc, seats);
    order.verify(jdbc).execute("select pg_advisory_xact_lock(417204)");
    order.verify(jdbc).queryForList(anyString(), eq(id));
    order.verify(seats).release(id);
  }
}
