package by.bsuir.travel.booking.repository;

import by.bsuir.travel.booking.model.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStatusHistoryEntityRepository
    extends JpaRepository<BookingStatusHistoryEntity, UUID> {}
