package by.bsuir.travel.payment.repository;

import by.bsuir.travel.payment.model.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCallbacksEntityRepository
    extends JpaRepository<PaymentCallbacksEntity, UUID> {}
