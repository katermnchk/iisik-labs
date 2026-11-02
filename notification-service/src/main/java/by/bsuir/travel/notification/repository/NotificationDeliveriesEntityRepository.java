package by.bsuir.travel.notification.repository;

import by.bsuir.travel.notification.model.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveriesEntityRepository
    extends JpaRepository<NotificationDeliveriesEntity, UUID> {}
