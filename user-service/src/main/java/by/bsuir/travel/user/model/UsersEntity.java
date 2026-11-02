package by.bsuir.travel.user.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UsersEntity {
  protected UsersEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "role_id")
  private UUID roleId;

  @Column(name = "email", length = 254)
  private String email;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "first_name", length = 80)
  private String firstName;

  @Column(name = "last_name", length = 80)
  private String lastName;

  @Column(name = "status", length = 20)
  private String status;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  public UUID getId() {
    return id;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
