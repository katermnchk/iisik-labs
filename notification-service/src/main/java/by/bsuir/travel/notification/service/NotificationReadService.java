package by.bsuir.travel.notification.service;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Clients.NotificationClient;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.notification.model.*;
import by.bsuir.travel.notification.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationReadService implements NotificationClient {
  private final NotificationsEntityRepository notifications;

  public NotificationReadService(NotificationsEntityRepository notifications) {
    this.notifications = notifications;
  }

  public Page<Notification> notifications(ListNotificationsRequest r) {
    ReadSupport.id(r.userId());
    return ReadSupport.page(
        notifications.findByUserId(UUID.fromString(r.userId())).stream()
            .map(
                n ->
                    new Notification(
                        n.getId().toString(),
                        n.getUserId().toString(),
                        n.getBookingId() == null ? null : n.getBookingId().toString(),
                        NotificationKind.valueOf(n.getKind()),
                        n.getTitle(),
                        n.getBody(),
                        DeliveryStatus.valueOf(n.getStatus()),
                        n.getCreatedAt()))
            .sorted(
                Comparator.comparing(Notification::createdAt)
                    .reversed()
                    .thenComparing(Notification::id))
            .toList(),
        r.page());
  }
}
