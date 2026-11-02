package by.bsuir.travel.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.contracts.Model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PaymentExpiryTest {
  final String bid = "00000000-0000-0000-0000-000000000601";
  final JdbcTemplate j = mock(JdbcTemplate.class);
  final PaymentReadService read = mock(PaymentReadService.class);
  final PaymentAdmission admission = mock(PaymentAdmission.class);

  PaymentSettlement service() {
    return new PaymentSettlement(j, read, null, admission, new ObjectMapper());
  }

  @Test
  void settledPaymentPreventsExpiryEvenBeforeBookingConsumerCatchesUp() {
    when(read.all())
        .thenReturn(
            List.of(
                new Payment(
                    "payment",
                    bid,
                    new Money(100, "BYN"),
                    PaymentStatus.SUCCEEDED,
                    "url",
                    OffsetDateTime.now())));
    assertFalse(service().expirePayment(bid));
    verifyNoInteractions(admission);
  }

  @Test
  void absentPaymentStillExpiresBookingThroughItsAuthority() {
    when(read.all()).thenReturn(List.of());
    when(admission.expireBooking(bid)).thenReturn(true);
    assertTrue(service().expirePayment(bid));
  }

  @Test
  void unexpiredBookingRemainsPayable() {
    when(read.all()).thenReturn(List.of());
    when(admission.expireBooking(bid)).thenReturn(false);
    assertFalse(service().expirePayment(bid));
    verify(j, never()).update(anyString(), any(Object[].class));
  }
}
