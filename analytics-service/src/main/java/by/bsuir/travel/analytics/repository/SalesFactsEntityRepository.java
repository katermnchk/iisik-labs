package by.bsuir.travel.analytics.repository;

import by.bsuir.travel.analytics.model.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesFactsEntityRepository extends JpaRepository<SalesFactsEntity, UUID> {}
