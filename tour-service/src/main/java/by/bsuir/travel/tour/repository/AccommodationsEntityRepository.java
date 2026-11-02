package by.bsuir.travel.tour.repository;

import by.bsuir.travel.tour.model.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccommodationsEntityRepository extends JpaRepository<AccommodationsEntity, UUID> {
  java.util.List<AccommodationsEntity> findByScheduleId(UUID id);
}
