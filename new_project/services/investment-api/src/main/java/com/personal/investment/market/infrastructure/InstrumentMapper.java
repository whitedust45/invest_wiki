package com.personal.investment.market.infrastructure;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InstrumentMapper {
  @Select("""
      SELECT instrument_id AS instrumentId, market, exchange, symbol, display_name AS displayName,
             asset_type AS assetType, native_currency AS nativeCurrency, maturity_date AS maturityDate, status,
             ts_code AS tushareCode, underlying_instrument_id AS underlyingInstrumentId
      FROM market_db.instrument
      WHERE market = #{market} AND exchange = #{exchange} AND symbol = #{symbol}
      LIMIT 1
      """)
  InstrumentRow findByNaturalKey(@Param("market") String market, @Param("exchange") String exchange,
      @Param("symbol") String symbol);

  @Select("""
      SELECT instrument_id AS instrumentId, market, exchange, symbol, display_name AS displayName,
             asset_type AS assetType, native_currency AS nativeCurrency, maturity_date AS maturityDate, status,
             ts_code AS tushareCode, underlying_instrument_id AS underlyingInstrumentId
      FROM market_db.instrument
      WHERE instrument_id = #{instrumentId}
      LIMIT 1
      """)
  InstrumentRow findById(@Param("instrumentId") String instrumentId);

  @Select("""
      SELECT instrument_id AS instrumentId, market, exchange, symbol, display_name AS displayName,
             asset_type AS assetType, native_currency AS nativeCurrency, maturity_date AS maturityDate, status,
             ts_code AS tushareCode, underlying_instrument_id AS underlyingInstrumentId
      FROM market_db.instrument ORDER BY instrument_id
      """)
  java.util.List<InstrumentRow> findAll();

  @Update("""
      UPDATE market_db.instrument SET ts_code = #{tushareCode}, updated_at = UTC_TIMESTAMP(3)
      WHERE instrument_id = #{instrumentId}
      """)
  int updateTushareCode(@Param("instrumentId") String instrumentId, @Param("tushareCode") String tushareCode);

  @Select("""
      SELECT product_code AS productCode, contract_multiplier_cent AS contractMultiplierCent
      FROM market_db.futures_contract
      WHERE instrument_id = #{instrumentId}
      LIMIT 1
      """)
  FutureRow findFutureByInstrumentId(@Param("instrumentId") String instrumentId);

  @Select("""
      SELECT underlying_instrument_id AS underlyingInstrumentId, option_right AS optionRight,
             strike_price_cent AS strikePriceCent, contract_multiplier AS contractMultiplier
      FROM market_db.option_contract
      WHERE instrument_id = #{instrumentId}
      LIMIT 1
      """)
  OptionRow findOptionByInstrumentId(@Param("instrumentId") String instrumentId);

  @Insert("""
      INSERT INTO market_db.instrument
        (instrument_id, market, exchange, symbol, ts_code, asset_type, display_name, native_currency,
         underlying_instrument_id, maturity_date, status, created_at, updated_at)
      VALUES
        (#{instrumentId}, #{market}, #{exchange}, #{symbol}, #{tushareCode}, #{assetType}, #{displayName},
         #{nativeCurrency}, #{underlyingInstrumentId}, #{maturityDate}, #{status}, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
      """)
  int insertInstrument(InstrumentRow instrument);

  @Insert("""
      INSERT INTO market_db.futures_contract
        (futures_contract_id, instrument_id, product_code, contract_multiplier_cent, currency, created_at)
      VALUES
        (#{futureContractId}, #{instrumentId}, #{productCode}, #{contractMultiplierCent}, #{currency},
         UTC_TIMESTAMP(3))
      """)
  int insertFuture(FutureInsertRow future);

  @Insert("""
      INSERT INTO market_db.option_contract
        (option_contract_id, instrument_id, underlying_instrument_id, option_right, strike_price_cent,
         contract_multiplier, currency, created_at)
      VALUES
        (#{optionContractId}, #{instrumentId}, #{underlyingInstrumentId}, #{optionRight},
         #{strikePriceCent}, #{contractMultiplier}, #{currency}, UTC_TIMESTAMP(3))
      """)
  int insertOption(OptionInsertRow option);

  record InstrumentRow(String instrumentId, String market, String exchange, String symbol, String displayName,
                       String assetType, String nativeCurrency, LocalDate maturityDate, String status,
                       String tushareCode, String underlyingInstrumentId) {
  }

  record FutureRow(String productCode, long contractMultiplierCent) {
  }

  record OptionRow(String underlyingInstrumentId, String optionRight, long strikePriceCent,
                   long contractMultiplier) {
  }

  record FutureInsertRow(String futureContractId, String instrumentId, String productCode,
                         long contractMultiplierCent, String currency) {
  }

  record OptionInsertRow(String optionContractId, String instrumentId, String underlyingInstrumentId,
                         String optionRight, long strikePriceCent, long contractMultiplier, String currency) {
  }
}
