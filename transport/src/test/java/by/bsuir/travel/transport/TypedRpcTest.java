package by.bsuir.travel.transport;

import static org.junit.jupiter.api.Assertions.*;

import by.bsuir.travel.contracts.Clients.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.DomainException;
import by.bsuir.travel.contracts.Model;
import by.bsuir.travel.proto.v1.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TypedRpcTest {
  private static final long LARGE = 9_007_199_254_740_993L;

  @Test
  void everyPublishedPortRoundTripsOverGeneratedProtobufMethods() throws Exception {
    var server = new RpcServer(new ObjectMapper(), 0, RpcClientTest.KEY);
    Map<Method, Object[]> actual = new java.util.concurrent.ConcurrentHashMap<>();
    var interfaces =
        RpcBindings.ALL.stream().map(b -> b.javaMethod().getDeclaringClass()).distinct().toList();
    for (var api : interfaces) {
      Object target =
          Proxy.newProxyInstance(
              api.getClassLoader(),
              new Class<?>[] {api},
              (proxy, method, arguments) -> {
                actual.put(method, arguments);
                return fixture(method.getGenericReturnType());
              });
      server.expose(api, target);
    }
    server.start();
    try (var client = new RpcClient(new ObjectMapper(), RpcClientTest.KEY)) {
      for (var binding : RpcBindings.ALL) {
        var method = binding.javaMethod();
        Object[] arguments =
            Arrays.stream(method.getGenericParameterTypes()).map(TypedRpcTest::fixture).toArray();
        Object port = client.proxy("localhost:" + server.boundPort(), method.getDeclaringClass());
        Object result = method.invoke(port, arguments);
        assertEquals(fixture(method.getGenericReturnType()), result, method.toString());
        assertArrayEquals(arguments, actual.get(method), method.toString());
        assertFalse(binding.rpc().getFullMethodName().contains("TravelRpc/Call"));
        assertNotNull(binding.rpc().getSchemaDescriptor());
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void optionalUpdatesPreserveNullEmptyZeroAndLargeInt64() {
    var update =
        new UpdateScheduleInput(
            "schedule",
            null,
            OffsetDateTime.parse("2026-11-02T12:34:56.123456789+03:00"),
            0,
            LARGE,
            null,
            LARGE);
    var encoded =
        (UpdateScheduleRequest) ProtoMapper.write(update, UpdateScheduleRequest.newBuilder());
    assertFalse(encoded.hasStartsAt());
    assertTrue(encoded.hasCapacity());
    assertEquals(0, encoded.getCapacity());
    assertEquals(LARGE, encoded.getPrice().getAmountMinor());
    assertEquals(LARGE, encoded.getExpectedVersion());
    var decoded = ProtoMapper.read(encoded, UpdateScheduleInput.class);
    assertEquals(update.endsAt().toInstant(), decoded.endsAt().toInstant());
    assertNull(decoded.startsAt());
    assertNull(decoded.status());
    assertEquals(update.capacity(), decoded.capacity());
    var user = new UpdateUserInput("user", null, null, "", null, "", null);
    assertEquals(
        user,
        ProtoMapper.read(
            ProtoMapper.write(user, UpdateUserRequest.newBuilder()), UpdateUserInput.class));
  }

  @Test
  void unknownEnumAndInvalidTimestampAreRejected() {
    var message = GetTourRequest.newBuilder().setTourId("a").setViewerRoleValue(999).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> ProtoMapper.read(message, Model.GetTourRequest.class));
    var request =
        UpdateScheduleRequest.newBuilder()
            .setScheduleId("a")
            .setExpectedVersion(1)
            .setStartsAt(com.google.protobuf.Timestamp.newBuilder().setNanos(-1))
            .build();
    assertThrows(
        IllegalArgumentException.class, () -> ProtoMapper.read(request, UpdateScheduleInput.class));
  }

  @Test
  void generatedStubReceivesNativeStatusesAndDomainTrailersAndAuthProtectsInvocation()
      throws Exception {
    var invocations = new AtomicInteger();
    UserClient target =
        new UserClient() {
          public Model.User user(Model.GetUserRequest request) {
            invocations.incrementAndGet();
            if (request.userId().equals("crash"))
              throw new IllegalStateException("private DB information");
            throw new DomainException(request.userId(), "public message");
          }

          public Model.Principal principal(Model.GetPrincipalRequest request) {
            return null;
          }
        };
    var server =
        new RpcServer(new ObjectMapper(), 0, RpcClientTest.KEY).expose(UserClient.class, target);
    server.start();
    var channel =
        ManagedChannelBuilder.forTarget("localhost:" + server.boundPort()).usePlaintext().build();
    try {
      var bare = UserServiceGrpc.newBlockingStub(channel);
      assertEquals(
          Status.Code.UNAUTHENTICATED,
          assertThrows(
                  StatusRuntimeException.class,
                  () -> bare.getUser(GetUserRequest.newBuilder().setUserId("NOT_FOUND").build()))
              .getStatus()
              .getCode());
      assertEquals(0, invocations.get());
      var metadata = new Metadata();
      metadata.put(RpcClient.KEY, RpcClientTest.KEY);
      var stub = bare.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
      var expected =
          Map.of(
              "BAD_REQUEST",
              Status.Code.INVALID_ARGUMENT,
              "NOT_FOUND",
              Status.Code.NOT_FOUND,
              "FORBIDDEN",
              Status.Code.PERMISSION_DENIED,
              "CONFLICT",
              Status.Code.ABORTED,
              "INSUFFICIENT_SEATS",
              Status.Code.RESOURCE_EXHAUSTED,
              "INVALID_STATE",
              Status.Code.FAILED_PRECONDITION);
      for (var entry : expected.entrySet()) {
        var error =
            assertThrows(
                StatusRuntimeException.class,
                () -> stub.getUser(GetUserRequest.newBuilder().setUserId(entry.getKey()).build()));
        assertEquals(entry.getValue(), error.getStatus().getCode());
        assertEquals(entry.getKey(), error.getTrailers().get(RpcClient.CODE));
      }
      var crash =
          assertThrows(
              StatusRuntimeException.class,
              () -> stub.getUser(GetUserRequest.newBuilder().setUserId("crash").build()));
      assertEquals(Status.Code.INTERNAL, crash.getStatus().getCode());
      assertFalse(crash.getMessage().contains("private DB"));
      try (var client = new RpcClient(new ObjectMapper(), RpcClientTest.KEY)) {
        var error =
            assertThrows(
                DomainException.class,
                () ->
                    client
                        .proxy("localhost:" + server.boundPort(), UserClient.class)
                        .user(new Model.GetUserRequest("NOT_FOUND")));
        assertEquals("NOT_FOUND", error.getCode());
        assertEquals("public message", error.getMessage());
      }
    } finally {
      channel.shutdownNow();
      server.stop();
    }
  }

  @Test
  void invalidTypedCommandNeverInvokesDomainHandler() {
    var called = new AtomicInteger();
    var api = TourCommands.class;
    Object target =
        Proxy.newProxyInstance(
            api.getClassLoader(),
            new Class<?>[] {api},
            (p, m, a) -> {
              called.incrementAndGet();
              return null;
            });
    var server = new RpcServer(new ObjectMapper(), 0, RpcClientTest.KEY).expose(api, target);
    server.start();
    var channel =
        ManagedChannelBuilder.forTarget("localhost:" + server.boundPort()).usePlaintext().build();
    try {
      var metadata = new Metadata();
      metadata.put(RpcClient.KEY, RpcClientTest.KEY);
      var stub =
          TourServiceGrpc.newBlockingStub(channel)
              .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
      var error =
          assertThrows(
              StatusRuntimeException.class,
              () -> stub.createTour(CreateTourRequest.getDefaultInstance()));
      assertEquals(Status.Code.INVALID_ARGUMENT, error.getStatus().getCode());
      assertEquals(0, called.get());
    } finally {
      channel.shutdownNow();
      server.stop();
    }
  }

  private static Object fixture(Type type) {
    Class<?> raw =
        type instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) type;
    if (raw == String.class) return "value";
    if (raw == boolean.class || raw == Boolean.class) return true;
    if (raw == int.class || raw == Integer.class) return 1;
    if (raw == long.class || raw == Long.class) return LARGE;
    if (raw == double.class) return 0.125;
    if (raw == OffsetDateTime.class) return OffsetDateTime.parse("2026-11-02T12:34:56.123456789Z");
    if (raw == LocalDate.class) return LocalDate.of(2026, 11, 2);
    if (raw.isEnum()) return raw.getEnumConstants()[0];
    if (raw == List.class)
      return List.of(fixture(((ParameterizedType) type).getActualTypeArguments()[0]));
    if (!raw.isRecord()) throw new IllegalArgumentException(raw.toString());
    var components = raw.getRecordComponents();
    Object[] values = new Object[components.length];
    Class<?>[] signature = new Class<?>[components.length];
    for (int i = 0; i < values.length; i++) {
      Type componentType = components[i].getGenericType();
      signature[i] = components[i].getType();
      if (componentType instanceof ParameterizedType p
          && p.getRawType() == List.class
          && p.getActualTypeArguments()[0] instanceof TypeVariable<?>)
        values[i] = List.of(fixture(((ParameterizedType) type).getActualTypeArguments()[0]));
      else if (raw == Model.Page.class
          && components[i].getName().equals("popularityAsOf")
          && ((ParameterizedType) type).getActualTypeArguments()[0] != Model.Tour.class)
        values[i] = null;
      else values[i] = fixture(componentType);
    }
    try {
      return raw.getDeclaredConstructor(signature).newInstance(values);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(raw.toString(), e);
    }
  }
}
