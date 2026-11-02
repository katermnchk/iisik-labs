package by.bsuir.travel.booking.repository;

import by.bsuir.travel.booking.model.*;
import java.time.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeysEntityRepository
    extends JpaRepository<IdempotencyKeysEntity, IdempotencyKeysEntityKey> {}
