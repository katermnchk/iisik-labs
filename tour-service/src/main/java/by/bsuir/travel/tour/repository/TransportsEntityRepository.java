package by.bsuir.travel.tour.repository;

import by.bsuir.travel.tour.model.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportsEntityRepository extends JpaRepository<TransportsEntity, UUID> {
  java.util.List<TransportsEntity> findByScheduleId(UUID id);
}
