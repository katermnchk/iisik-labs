package by.bsuir.travel.gateway;

import static org.junit.jupiter.api.Assertions.*;

import graphql.schema.idl.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@SpringBootTest
class FutureSchemaTest {
  @Autowired RuntimeWiringConfigurer scalars;

  @Test
  void futureSchemaHasValidTypesAndEnums() throws Exception {
    Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (base != null && !Files.exists(base.resolve("docs/graphql-future.graphqls")))
      base = base.getParent();
    assertNotNull(base, "Repository root must contain future schema");
    var types =
        new SchemaParser().parse(Files.readString(base.resolve("docs/graphql-future.graphqls")));
    var wiring = RuntimeWiring.newRuntimeWiring();
    scalars.configure(wiring);
    var schema = new SchemaGenerator().makeExecutableSchema(types, wiring.build());
    assertNotNull(schema.getMutationType().getFieldDefinition("updateUser"));
    assertEquals(8, schema.getQueryType().getFieldDefinitions().size());
  }
}
