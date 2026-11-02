package by.bsuir.travel.payment.repository;

import by.bsuir.travel.payment.model.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentsEntityRepository extends JpaRepository<PaymentsEntity, UUID> {
  java.util.List<PaymentsEntity> findByBookingIdIn(java.util.Collection<UUID> ids);
}
