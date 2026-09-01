package com.personal.investment.identity.domain;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;

public final class UlidGenerator {
  private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();

  private UlidGenerator() {
  }

  public static String next() {
    return next(Clock.systemUTC());
  }

  static String next(Clock clock) {
    byte[] bytes = new byte[16];
    long epochMillis = clock.millis();
    for (int index = 5; index >= 0; index--) {
      bytes[index] = (byte) epochMillis;
      epochMillis >>>= 8;
    }
    byte[] randomness = new byte[10];
    RANDOM.nextBytes(randomness);
    System.arraycopy(randomness, 0, bytes, 6, randomness.length);

    BigInteger value = new BigInteger(1, bytes);
    char[] encoded = new char[26];
    for (int index = encoded.length - 1; index >= 0; index--) {
      BigInteger[] quotientAndRemainder = value.divideAndRemainder(BigInteger.valueOf(32));
      encoded[index] = CROCKFORD[quotientAndRemainder[1].intValue()];
      value = quotientAndRemainder[0];
    }
    return new String(encoded);
  }
}
