package by.bsuir.travel.gateway.config;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.contracts.Model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AuthClient {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
  private final String base;
  private final ObjectMapper mapper;

  public AuthClient(Environment e, ObjectMapper m) {
    base = e.getProperty("AUTH_URL", "http://localhost:8190");
    mapper = m;
  }

  public <T> T call(String path, Object input, String token, Class<T> type) {
    try {
      var b =
          HttpRequest.newBuilder(URI.create(base + path))
              .timeout(Duration.ofSeconds(4))
              .header("Content-Type", "application/json");
      if (token != null) b.header("Authorization", "Bearer " + token);
      var response =
          http.send(
              b.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(input))).build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        var err = mapper.readTree(response.body());
        throw new DomainException(
            err.path("code").asText("SERVICE_UNAVAILABLE"),
            err.path("message").asText("Authentication service unavailable"));
      }
      return mapper.readValue(response.body(), type);
    } catch (DomainException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DomainException("SERVICE_UNAVAILABLE", "Authentication interrupted");
    } catch (Exception e) {
      throw new DomainException("SERVICE_UNAVAILABLE", "Authentication service unavailable");
    }
  }
}
