package by.bsuir.travel.gateway;

import static org.junit.jupiter.api.Assertions.*;

import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.gateway.mock.MockData;
import by.bsuir.travel.gateway.mock.MockTourClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DirectionCatalogTest {
  private MockTourClient client(boolean active) {
    Direction direction = new Direction("00000000-0000-0000-0000-000000000104", "Беларусь", "Минск", "Без туров", active);
    MockData source = new MockData(new ObjectMapper()) {
      @Override public <T> List<T> list(String name, Class<T> type) {
        return type == Direction.class ? List.of(type.cast(direction)) : List.of();
      }
    };
    return new MockTourClient(source);
  }

  @Test
  void activeDirectionWithoutToursIsStillInCatalog() {
    var result = client(true).directions(new ListDirectionsRequest(true));
    assertEquals(1, result.size());
    assertEquals("Минск", result.get(0).city());
  }

  @Test
  void activeOnlyFiltersCatalogIndependentlyOfTours() {
    var client = client(false);
    assertTrue(client.directions(new ListDirectionsRequest(true)).isEmpty());
    assertEquals(1, client.directions(new ListDirectionsRequest(false)).size());
  }
}
