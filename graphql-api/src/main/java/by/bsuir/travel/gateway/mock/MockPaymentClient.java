package by.bsuir.travel.gateway.mock;

import by.bsuir.travel.contracts.Clients.PaymentClient;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.contracts.ReadSupport;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentClient implements PaymentClient {
  private final List<Payment> payments;

  public MockPaymentClient(MockData data) {
    payments = data.list("payments", Payment.class);
  }

  public List<Payment> payments(ListPaymentsRequest r) {
    return payments.stream()
        .filter(p -> r.bookingIds().stream().anyMatch(id -> ReadSupport.sameId(p.bookingId(), id)))
        .sorted(Comparator.comparing(Payment::createdAt).thenComparing(Payment::id))
        .toList();
  }
}
