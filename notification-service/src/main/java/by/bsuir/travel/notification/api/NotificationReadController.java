package by.bsuir.travel.notification.api;

import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.notification.service.NotificationReadService;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1")
public class NotificationReadController {
  private final NotificationReadService service;

  public NotificationReadController(NotificationReadService service) {
    this.service = service;
  }

  @GetMapping("/notifications")
  public Page<Notification> notifications(
      @RequestParam String userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.notifications(new ListNotificationsRequest(userId, new PageInput(page, size)));
  }
}
