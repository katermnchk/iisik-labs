package by.bsuir.travel.payment.repository;

import by.bsuir.travel.payment.model.*;
import java.time.*;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentsEntityRepository extends JpaRepository<PaymentsEntity, UUID> {
  List<PaymentsEntity> findByBookingIdIn(Collection<UUID> ids);
}
