package by.bsuir.travel.analytics.repository;

import by.bsuir.travel.analytics.model.*;
import java.time.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectionPopularityDailyEntityRepository
    extends JpaRepository<DirectionPopularityDailyEntity, DirectionPopularityDailyEntityKey> {}
