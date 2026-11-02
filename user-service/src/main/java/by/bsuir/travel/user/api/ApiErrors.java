package by.bsuir.travel.user.api;

import by.bsuir.travel.contracts.DomainException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<Map<String, String>> domain(DomainException ex) {
    return ResponseEntity.status(
            switch (ex.getCode()) {
              case "NOT_FOUND" -> 404;
              case "FORBIDDEN" -> 403;
              case "UNAUTHENTICATED" -> 401;
              case "BAD_REQUEST" -> 400;
              default -> 409;
            })
        .body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> conflict(Exception ex) {
    return ResponseEntity.status(409)
        .body(Map.of("code", "CONFLICT", "message", "Operation conflicts with stored data"));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> input(IllegalArgumentException ex) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "BAD_REQUEST", "message", "Invalid input"));
  }
}
