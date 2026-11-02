package by.bsuir.travel.gateway.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class MockData {
  private final ObjectMapper mapper;

  public MockData(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public <T> List<T> list(String name, Class<T> type) {
    try (var stream = new ClassPathResource("mock/" + name + ".json").getInputStream()) {
      return mapper.readValue(
          stream, mapper.getTypeFactory().constructCollectionType(List.class, type));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load mock data: " + name, e);
    }
  }

  public <T> T object(String name, Class<T> type) {
    try (var stream = new ClassPathResource("mock/" + name + ".json").getInputStream()) {
      return mapper.readValue(stream, type);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load mock data: " + name, e);
    }
  }
}
