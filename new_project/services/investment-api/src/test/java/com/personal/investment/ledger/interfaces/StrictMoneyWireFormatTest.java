package com.personal.investment.ledger.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.junit.jupiter.api.Test;

class StrictMoneyWireFormatTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void acceptsOnlyStringTokenAndPreservesLongRangeMinorUnits() throws Exception {
    AmountRequest request = objectMapper.readValue("{\"amountCent\":\"9223372036854775807\"}",
        AmountRequest.class);

    assertThat(request.amountCent()).isEqualTo("9223372036854775807");
    assertThat(PositiveMinorUnitParser.parse(request.amountCent(), "amount_cent"))
        .isEqualTo(Long.MAX_VALUE);
    assertThatThrownBy(() -> objectMapper.readValue("{\"amountCent\":666}", AmountRequest.class))
        .hasMessageContaining("String");
  }

  @Test
  void rejectsZeroNegativeAndOverflowMinorUnitStrings() {
    assertThatIllegalArgumentException().isThrownBy(() -> PositiveMinorUnitParser.parse("0", "amount_cent"));
    assertThatIllegalArgumentException().isThrownBy(() -> PositiveMinorUnitParser.parse("-1", "amount_cent"));
    assertThatIllegalArgumentException().isThrownBy(() -> PositiveMinorUnitParser.parse("9223372036854775808", "amount_cent"));
  }

  private record AmountRequest(@JsonDeserialize(using = StrictStringDeserializer.class) String amountCent) {
  }
}
