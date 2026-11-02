package by.bsuir.travel.tour.repository;

import by.bsuir.travel.tour.model.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourSchedulesEntityRepository extends JpaRepository<TourSchedulesEntity, UUID> {
  java.util.List<TourSchedulesEntity> findByTourId(UUID id);
}
