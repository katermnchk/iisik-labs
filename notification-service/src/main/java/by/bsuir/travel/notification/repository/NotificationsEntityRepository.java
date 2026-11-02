package by.bsuir.travel.notification.repository;

import by.bsuir.travel.notification.model.*;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationsEntityRepository extends JpaRepository<NotificationsEntity, UUID> {
  List<NotificationsEntity> findByUserId(UUID id);
}
