package by.bsuir.travel.user.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class RolesEntity {
  protected RolesEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "code", length = 20)
  private String code;

  @Column(name = "name", length = 80)
  private String name;

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }
}
