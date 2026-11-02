package by.bsuir.travel.tour.repository;

import by.bsuir.travel.tour.model.*;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportsEntityRepository extends JpaRepository<TransportsEntity, UUID> {
  List<TransportsEntity> findByScheduleId(UUID id);
}
