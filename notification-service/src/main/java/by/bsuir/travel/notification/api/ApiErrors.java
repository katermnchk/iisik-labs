package by.bsuir.travel.notification.api;

import by.bsuir.travel.contracts.DomainException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<Map<String, String>> domain(DomainException ex) {
    return ResponseEntity.status(
            ex.getCode().equals("NOT_FOUND") ? 404 : ex.getCode().equals("FORBIDDEN") ? 403 : 400)
        .body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> input(IllegalArgumentException ex) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "BAD_REQUEST", "message", "Invalid input"));
  }
}
