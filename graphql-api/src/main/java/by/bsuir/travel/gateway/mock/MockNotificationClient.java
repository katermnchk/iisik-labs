package by.bsuir.travel.gateway.mock;

import by.bsuir.travel.contracts.Clients.NotificationClient;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.contracts.ReadSupport;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MockNotificationClient implements NotificationClient {
  private final List<Notification> notifications;

  public MockNotificationClient(MockData data) {
    notifications = data.list("notifications", Notification.class);
  }

  public Page<Notification> notifications(ListNotificationsRequest r) {
    return ReadSupport.page(
        notifications.stream()
            .filter(n -> ReadSupport.sameId(n.userId(), r.userId()))
            .sorted(
                Comparator.comparing(Notification::createdAt)
                    .reversed()
                    .thenComparing(Notification::id))
            .toList(),
        r.page());
  }
}
