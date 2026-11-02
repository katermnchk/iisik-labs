package by.bsuir.travel.payment.api;

import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.payment.service.PaymentReadService;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1")
public class PaymentReadController {
  private final PaymentReadService service;

  public PaymentReadController(PaymentReadService service) {
    this.service = service;
  }

  @GetMapping("/payments")
  public List<Payment> payments(@RequestParam List<String> bookingIds) {
    return service.payments(new ListPaymentsRequest(bookingIds));
  }
}
