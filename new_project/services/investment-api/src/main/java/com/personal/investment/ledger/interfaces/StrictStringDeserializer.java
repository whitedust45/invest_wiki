package com.personal.investment.ledger.interfaces;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Rejects JSON numeric tokens so monetary values can never pass through a JavaScript number. */
public class StrictStringDeserializer extends JsonDeserializer<String> {
  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.currentToken() != JsonToken.VALUE_STRING) {
      return (String) context.handleUnexpectedToken(String.class, parser);
    }
    return parser.getText();
  }
}
