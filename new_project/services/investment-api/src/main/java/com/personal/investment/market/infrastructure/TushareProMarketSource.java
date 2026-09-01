package com.personal.investment.market.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.bootstrap.config.TushareProProperties;
import com.personal.investment.market.application.MarketAutomaticSource;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Official Tushare Pro JSON client. It deliberately contains no scrape or undocumented fallback provider. */
@Component
public class TushareProMarketSource implements MarketAutomaticSource {
  private static final DateTimeFormatter TUSHARE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private final ObjectMapper json;
  private final TushareProProperties properties;
  private final HttpClient client;

  @Autowired
  public TushareProMarketSource(ObjectMapper json, TushareProProperties properties) {
    this(json, properties, HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
  }

  TushareProMarketSource(ObjectMapper json, TushareProProperties properties, HttpClient client) {
    this.json = json;
    this.properties = properties;
    this.client = client;
  }

  @Override
  public MarketAutomaticSourceResult refresh(List<Instrument> instruments, LocalDate tradingDate) {
    if (!properties.configured()) {
      return new MarketAutomaticSourceResult(List.of(), List.of(), List.of(new Issue(null, "TUSHARE_UNAVAILABLE",
          "TUSHARE_TOKEN is not configured")));
    }
    List<Quote> quotes = new ArrayList<>();
    List<Metric> metrics = new ArrayList<>();
    List<Issue> issues = new ArrayList<>();
    for (Instrument instrument : instruments) {
      try {
        fetchQuote(instrument, tradingDate).ifPresent(quotes::add);
        if (instrument.assetType() == AssetType.INDEX) {
          metrics.addAll(fetchPbHistory(instrument, tradingDate));
        }
      } catch (TushareCallException exception) {
        issues.add(new Issue(instrument.instrumentId(), exception.code, exception.getMessage()));
      } catch (RuntimeException exception) {
        issues.add(new Issue(instrument.instrumentId(), "TUSHARE_RESPONSE_INVALID", "Tushare response was invalid"));
      }
    }
    return new MarketAutomaticSourceResult(quotes, metrics, issues);
  }

  private java.util.Optional<Quote> fetchQuote(Instrument instrument, LocalDate tradingDate) {
    String apiName = apiForQuote(instrument);
    Map<String, String> row = call(apiName, Map.of("ts_code", instrument.tushareCode(), "trade_date",
        TUSHARE_DATE.format(tradingDate)), "ts_code,trade_date,close,pre_close,settle").stream().findFirst()
        .orElse(null);
    if (row == null) {
      return java.util.Optional.empty();
    }
    String close = firstPresent(row, "close", "settle");
    if (close == null) {
      throw new TushareCallException("TUSHARE_REQUIRED_FIELD_MISSING", "Tushare quote omitted close or settle");
    }
    String previous = firstPresent(row, "pre_close");
    long closeCent = toMinorUnitExact(close);
    Long previousCent = previous == null || previous.isBlank() ? null : toMinorUnitExact(previous);
    Instant quoteTime = tradingDate.atTime(8, 0).toInstant(ZoneOffset.UTC);
    return java.util.Optional.of(new Quote(instrument.instrumentId(), quoteTime,
        instrument.tushareCode() + "-" + TUSHARE_DATE.format(tradingDate), closeCent, previousCent));
  }

  private List<Metric> fetchPbHistory(Instrument instrument, LocalDate tradingDate) {
    List<Metric> values = new ArrayList<>();
    for (Map<String, String> row : call("index_dailybasic", Map.of("ts_code", instrument.tushareCode(),
        "start_date", TUSHARE_DATE.format(tradingDate.minusYears(10)), "end_date", TUSHARE_DATE.format(tradingDate)),
        "ts_code,trade_date,pb")) {
      String pb = firstPresent(row, "pb");
      String date = firstPresent(row, "trade_date");
      if (pb == null || pb.isBlank() || date == null || date.isBlank()) {
        continue;
      }
      try {
        LocalDate metricDate = LocalDate.parse(date, TUSHARE_DATE);
        BigDecimal value = new BigDecimal(pb);
        if (value.signum() > 0 && value.scale() <= 12) {
          values.add(new Metric(instrument.instrumentId(), metricDate, "PB", value,
              instrument.tushareCode() + "-pb-" + date));
        }
      } catch (RuntimeException ignored) {
        // A malformed row is not silently converted; the provider-level result remains auditable through missing data.
      }
    }
    return values;
  }

  private List<Map<String, String>> call(String apiName, Map<String, String> params, String fields) {
    try {
      Map<String, Object> requestPayload = new LinkedHashMap<>();
      requestPayload.put("api_name", apiName);
      requestPayload.put("token", properties.token());
      requestPayload.put("params", params);
      requestPayload.put("fields", fields);
      HttpRequest request = HttpRequest.newBuilder(properties.endpoint())
          .timeout(properties.requestTimeout())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(requestPayload)))
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new TushareCallException("TUSHARE_HTTP_" + response.statusCode(), "Tushare HTTP request failed");
      }
      return rows(json.readTree(response.body()));
    } catch (TushareCallException exception) {
      throw exception;
    } catch (java.net.http.HttpTimeoutException exception) {
      throw new TushareCallException("TUSHARE_TIMEOUT", "Tushare request timed out");
    } catch (java.io.IOException exception) {
      throw new TushareCallException("TUSHARE_NETWORK", "Tushare network request failed");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new TushareCallException("TUSHARE_INTERRUPTED", "Tushare request was interrupted");
    } catch (Exception exception) {
      throw new TushareCallException("TUSHARE_RESPONSE_INVALID", "Tushare response could not be parsed");
    }
  }

  private static List<Map<String, String>> rows(JsonNode root) {
    int code = root.path("code").asInt(0);
    if (code != 0) {
      throw new TushareCallException("TUSHARE_API_" + code, "Tushare API rejected the request");
    }
    JsonNode data = root.path("data");
    JsonNode fields = data.path("fields");
    JsonNode items = data.path("items");
    if (!fields.isArray() || !items.isArray()) {
      throw new TushareCallException("TUSHARE_RESPONSE_INVALID", "Tushare response lacks tabular data");
    }
    List<String> names = new ArrayList<>();
    for (JsonNode field : fields) {
      names.add(field.asText());
    }
    List<Map<String, String>> values = new ArrayList<>();
    for (JsonNode item : items) {
      if (!item.isArray() || item.size() != names.size()) {
        throw new TushareCallException("TUSHARE_RESPONSE_INVALID", "Tushare row width is invalid");
      }
      Map<String, String> row = new LinkedHashMap<>();
      for (int index = 0; index < names.size(); index++) {
        JsonNode value = item.get(index);
        row.put(names.get(index), value == null || value.isNull() ? null : value.asText());
      }
      values.add(row);
    }
    return values;
  }

  static long toMinorUnitExact(String value) {
    try {
      return new BigDecimal(value).movePointRight(2).longValueExact();
    } catch (RuntimeException exception) {
      throw new TushareCallException("PRICE_PRECISION_UNREPRESENTABLE",
          "Tushare price cannot be represented in original-currency cents");
    }
  }

  private static String apiForQuote(Instrument instrument) {
    if (instrument.assetType() == AssetType.FUTURE) {
      return "fut_daily";
    }
    if (instrument.assetType() == AssetType.INDEX) {
      return "index_daily";
    }
    if ("US".equals(instrument.market())) {
      return "us_daily";
    }
    if (instrument.assetType() == AssetType.ETF) {
      return "fund_daily";
    }
    return "daily";
  }

  private static String firstPresent(Map<String, String> row, String... keys) {
    for (String key : keys) {
      if (row.get(key) != null) {
        return row.get(key);
      }
    }
    return null;
  }

  private static final class TushareCallException extends RuntimeException {
    private final String code;

    private TushareCallException(String code, String message) {
      super(message);
      this.code = code;
    }
  }
}
