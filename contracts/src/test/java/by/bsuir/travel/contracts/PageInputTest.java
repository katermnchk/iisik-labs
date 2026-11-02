package by.bsuir.travel.contracts;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PageInputTest {
  @Test
  void rejectsInvalidPageSize() {
    assertThrows(IllegalArgumentException.class, () -> new Model.PageInput(0, 0));
    assertThrows(IllegalArgumentException.class, () -> new Model.PageInput(0, 101));
    assertDoesNotThrow(() -> new Model.PageInput(0, 1));
    assertDoesNotThrow(() -> new Model.PageInput(0, 100));
  }

  @Test
  void rejectsNegativePage() {
    assertThrows(IllegalArgumentException.class, () -> new Model.PageInput(-1, 20));
  }
}
