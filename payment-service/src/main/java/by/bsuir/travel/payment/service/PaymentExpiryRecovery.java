package by.bsuir.travel.payment.service;

import by.bsuir.travel.contracts.Model.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Retries the Payment half if a crash followed Booking's durable expiry decision. */
@Service
public class PaymentExpiryRecovery {
  private final PaymentReadService read;
  private final PaymentSettlement settlement;
  private final Logger log = LoggerFactory.getLogger(PaymentExpiryRecovery.class);

  public PaymentExpiryRecovery(PaymentReadService read, PaymentSettlement settlement) {
    this.read = read;
    this.settlement = settlement;
  }

  @Scheduled(fixedDelay = 10000)
  public void recover() {
    for (var p : read.all()) {
      if (p.status() != PaymentStatus.PENDING) continue;
      try {
        settlement.expirePayment(p.bookingId());
      } catch (Exception e) {
        log.warn("payment expiry deferred paymentId={} error={}", p.id(), e.toString());
      }
    }
  }
}
