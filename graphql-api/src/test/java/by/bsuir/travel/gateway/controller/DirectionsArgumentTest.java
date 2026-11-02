package by.bsuir.travel.gateway.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import by.bsuir.travel.gateway.service.TravelQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;

class DirectionsArgumentTest {
  @Test
  void explicitNullUsesActiveDirectionsDefault() {
    var service = mock(TravelQueryService.class);
    when(service.directions(true, null)).thenReturn(List.of());
    var controller = new QueryController(service, null);
    assertEquals(List.of(), controller.directions((Boolean) null, null));
    verify(service).directions(true, null);
  }

  @Test
  void explicitFalseIncludesInactiveDirections() {
    var service = mock(TravelQueryService.class);
    new QueryController(service, null).directions(false, null);
    verify(service).directions(false, null);
  }
}
