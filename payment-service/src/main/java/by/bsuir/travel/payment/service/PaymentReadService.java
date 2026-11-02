package by.bsuir.travel.payment.service;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Clients.PaymentClient;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.payment.model.*;
import by.bsuir.travel.payment.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentReadService implements PaymentClient {
  private final PaymentsEntityRepository payments;

  public PaymentReadService(PaymentsEntityRepository payments) {
    this.payments = payments;
  }

  public List<Payment> all() {
    return payments.findAll().stream()
        .map(
            p ->
                new Payment(
                    p.getId().toString(),
                    p.getBookingId().toString(),
                    new Money(p.getAmountMinor(), p.getCurrency()),
                    PaymentStatus.valueOf(p.getStatus()),
                    p.getPaymentUrl(),
                    p.getCreatedAt()))
        .toList();
  }

  public List<Payment> payments(ListPaymentsRequest r) {
    r.bookingIds().forEach(ReadSupport::id);
    return payments
        .findByBookingIdIn(r.bookingIds().stream().map(UUID::fromString).toList())
        .stream()
        .map(
            p ->
                new Payment(
                    p.getId().toString(),
                    p.getBookingId().toString(),
                    new Money(p.getAmountMinor(), p.getCurrency()),
                    PaymentStatus.valueOf(p.getStatus()),
                    p.getPaymentUrl(),
                    p.getCreatedAt()))
        .sorted(Comparator.comparing(Payment::createdAt).thenComparing(Payment::id))
        .toList();
  }
}
