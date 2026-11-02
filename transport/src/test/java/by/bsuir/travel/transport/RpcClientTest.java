package by.bsuir.travel.transport;

import static org.junit.jupiter.api.Assertions.*;

import by.bsuir.travel.contracts.Clients.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.DomainException;
import by.bsuir.travel.contracts.Model;
import by.bsuir.travel.proto.v1.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RpcClientTest {
  static final String KEY = "0123456789abcdef0123456789abcdef";

  @Test
  void transientReadRetriesThenSucceeds() throws Exception {
    var calls = new AtomicInteger();
    var server =
        ServerBuilder.forPort(0)
            .addService(
                new UserServiceGrpc.UserServiceImplBase() {
                  public void getUser(
                      GetUserRequest request, StreamObserver<GetUserResponse> observer) {
                    if (calls.incrementAndGet() < 3)
                      observer.onError(Status.UNAVAILABLE.asRuntimeException());
                    else {
                      observer.onNext(
                          GetUserResponse.newBuilder()
                              .setUser(User.newBuilder().setId("result"))
                              .build());
                      observer.onCompleted();
                    }
                  }
                })
            .build()
            .start();
    try (var client = new RpcClient(new ObjectMapper(), KEY)) {
      assertEquals(
          "result",
          client
              .proxy("localhost:" + server.getPort(), UserClient.class)
              .user(new Model.GetUserRequest("a"))
              .id());
      assertEquals(3, calls.get());
    } finally {
      server.shutdownNow();
    }
  }

  @Test
  void nonIdempotentWriteIsNotRetried() throws Exception {
    var calls = new AtomicInteger();
    var server =
        ServerBuilder.forPort(0)
            .addService(
                new TourServiceGrpc.TourServiceImplBase() {
                  public void createTour(
                      CreateTourRequest request, StreamObserver<GetTourResponse> observer) {
                    calls.incrementAndGet();
                    observer.onError(Status.UNAVAILABLE.asRuntimeException());
                  }
                })
            .build()
            .start();
    try (var client = new RpcClient(new ObjectMapper(), KEY)) {
      assertThrows(
          DomainException.class,
          () ->
              client
                  .proxy("localhost:" + server.getPort(), TourCommands.class)
                  .createTour(new CreateTourInput("a", "title", "description", 1), null));
      assertEquals(1, calls.get());
    } finally {
      server.shutdownNow();
    }
  }

  @Test
  void unresponsiveReadHasFiniteDeadlines() throws Exception {
    var calls = new AtomicInteger();
    var server =
        ServerBuilder.forPort(0)
            .addService(
                new UserServiceGrpc.UserServiceImplBase() {
                  public void getUser(
                      GetUserRequest request, StreamObserver<GetUserResponse> observer) {
                    calls.incrementAndGet();
                  }
                })
            .build()
            .start();
    long start = System.nanoTime();
    try (var client = new RpcClient(new ObjectMapper(), KEY)) {
      assertThrows(
          DomainException.class,
          () ->
              client
                  .proxy("localhost:" + server.getPort(), UserClient.class)
                  .user(new Model.GetUserRequest("a")));
      assertEquals(3, calls.get());
      long elapsed = (System.nanoTime() - start) / 1000000;
      assertTrue(elapsed >= 5900 && elapsed < 10000, "bounded deadline duration: " + elapsed);
    } finally {
      server.shutdownNow();
    }
  }
}
