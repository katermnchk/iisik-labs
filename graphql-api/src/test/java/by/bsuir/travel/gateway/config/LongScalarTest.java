package by.bsuir.travel.gateway.config;

import static org.junit.jupiter.api.Assertions.*;

import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.*;
import graphql.schema.idl.RuntimeWiring;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class LongScalarTest {
  private Coercing<?, ?> coercing() {
    var builder = RuntimeWiring.newRuntimeWiring();
    new GraphqlConfig().scalars().configure(builder);
    return builder.build().getScalars().get("Long").getCoercing();
  }

  @Test
  void serializesFull64BitValueWithoutPrecisionLoss() {
    assertEquals("9223372036854775807", coercing().serialize(Long.MAX_VALUE));
  }

  @Test
  void acceptsIntegerAndStringLiterals() {
    assertEquals(
        90000L, coercing().parseLiteral(IntValue.newIntValue(BigInteger.valueOf(90000)).build()));
    assertEquals(
        Long.MIN_VALUE,
        coercing().parseLiteral(StringValue.newStringValue("-9223372036854775808").build()));
  }

  @Test
  void rejectsFractionalAndOverflowVariables() {
    assertThrows(
        CoercingParseValueException.class, () -> coercing().parseValue(new BigDecimal("1.5")));
    assertThrows(
        CoercingParseValueException.class, () -> coercing().parseValue("9223372036854775808"));
  }

  @Test
  void invalidStringLiteralProducesLiteralError() {
    assertThrows(
        CoercingParseLiteralException.class,
        () -> coercing().parseLiteral(StringValue.newStringValue("invalid").build()));
  }

  @Test
  void rejectsNonIntegerLiteral() {
    assertThrows(
        CoercingParseLiteralException.class,
        () -> coercing().parseLiteral(FloatValue.newFloatValue(new BigDecimal("1.5")).build()));
  }
}
