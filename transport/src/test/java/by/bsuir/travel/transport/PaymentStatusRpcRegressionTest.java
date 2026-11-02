package by.bsuir.travel.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import by.bsuir.travel.contracts.Model;
import by.bsuir.travel.proto.v1.Payment;
import org.junit.jupiter.api.Test;

class PaymentStatusRpcRegressionTest {
  @Test
  void everyPaymentStatusSurvivesProtobufRoundTrip() {
    for (var status : Model.PaymentStatus.values()) {
      var original = new Model.Payment("payment", "booking", null, status, null, null);
      var encoded = ProtoMapper.write(original, Payment.newBuilder());
      var decoded = ProtoMapper.read(encoded, Model.Payment.class);
      assertEquals(status, decoded.status(), "Payment status " + status);
    }
  }
}
