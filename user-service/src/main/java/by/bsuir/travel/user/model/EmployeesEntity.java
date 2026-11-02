package by.bsuir.travel.user.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "employees")
public class EmployeesEntity {
  protected EmployeesEntity() {}

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "position", length = 120)
  private String position;

  public UUID getUserId() {
    return userId;
  }

  public String getPosition() {
    return position;
  }
}
