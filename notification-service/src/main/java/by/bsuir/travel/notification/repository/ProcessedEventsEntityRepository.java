package by.bsuir.travel.notification.repository;

import by.bsuir.travel.notification.model.*;
import java.time.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventsEntityRepository
    extends JpaRepository<ProcessedEventsEntity, ProcessedEventsEntityKey> {}
