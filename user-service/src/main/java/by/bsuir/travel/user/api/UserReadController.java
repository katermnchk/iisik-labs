package by.bsuir.travel.user.api;

import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.user.service.UserReadService;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1")
public class UserReadController {
  private final UserReadService service;

  public UserReadController(UserReadService service) {
    this.service = service;
  }

  @GetMapping("/users/{id}")
  public User user(@PathVariable String id) {
    return service.user(new GetUserRequest(id));
  }

  @GetMapping("/users")
  public List<User> all() {
    return service.all();
  }
}
