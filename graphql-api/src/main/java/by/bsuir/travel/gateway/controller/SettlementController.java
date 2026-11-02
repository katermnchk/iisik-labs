package by.bsuir.travel.gateway.controller;

import by.bsuir.travel.contracts.Identity.Settlement;
import by.bsuir.travel.contracts.Model.*;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

@Controller
public class SettlementController {
  private final Settlement s;

  public SettlementController(Settlement s) {
    this.s = s;
  }

  @MutationMapping
  public Payment confirmPayment(@Argument String paymentId, @ContextValue Principal principal) {
    return s.confirmPayment(paymentId, principal);
  }
}
