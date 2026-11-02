package by.bsuir.travel.user.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "clients")
public class ClientsEntity {
  protected ClientsEntity() {}

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "phone", length = 32)
  private String phone;

  public UUID getUserId() {
    return userId;
  }

  public String getPhone() {
    return phone;
  }
}
