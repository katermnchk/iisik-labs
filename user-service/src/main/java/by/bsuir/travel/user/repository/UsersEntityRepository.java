package by.bsuir.travel.user.repository;

import by.bsuir.travel.user.model.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersEntityRepository extends JpaRepository<UsersEntity, UUID> {}
