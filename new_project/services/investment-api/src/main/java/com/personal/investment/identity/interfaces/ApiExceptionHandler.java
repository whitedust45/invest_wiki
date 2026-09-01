package com.personal.investment.identity.interfaces;

import com.personal.investment.bootstrap.config.TraceIdFilter;
import com.personal.investment.identity.application.AuthException;
import com.personal.investment.ledger.application.InsufficientBalanceException;
import com.personal.investment.ledger.application.AccountDisableRejectedException;
import com.personal.investment.ledger.application.AccountVersionConflictException;
import com.personal.investment.ledger.application.ReplayInvariantViolationException;
import com.personal.investment.ledger.application.LedgerSnapshotRestoreRejectedException;
import com.personal.investment.ledger.application.CorporateActionNoOpenPositionException;
import com.personal.investment.ledger.application.CorporateActionRatioException;
import com.personal.investment.ledger.application.CorporateActionUnsupportedException;
import com.personal.investment.ledger.application.CorrectionRejectedException;
import com.personal.investment.ledger.domain.InsufficientPositionException;
import com.personal.investment.ledger.domain.PricePrecisionException;
import com.personal.investment.ledger.interfaces.TransactionFieldsException;
import com.personal.investment.market.domain.InstrumentConflictException;
import com.personal.investment.market.application.MarketSnapshotValidationException;
import com.personal.investment.platform.application.IdempotencyException;
import com.personal.investment.portfolio.application.PortfolioResourceNotFoundException;
import com.personal.investment.strategy.application.StrategyRuleVersionConflictException;
import com.personal.investment.strategy.application.StrategySeedRequiresEmptyLedgerException;
import com.personal.investment.strategy.application.StrategyValidationException;
import com.personal.investment.strategy.domain.StrategyNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(AuthException.class)
  ResponseEntity<ApiProblemResponse> handleAuth(AuthException exception, HttpServletRequest request) {
    return response(exception.status(), exception.code(), exception.getMessage(), List.of(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiProblemResponse> handleValidation(MethodArgumentNotValidException exception,
      HttpServletRequest request) {
    List<String> details = exception.getBindingResult().getAllErrors().stream()
        .map(error -> error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName())
        .toList();
    if (request.getRequestURI().startsWith("/api/v1/market/snapshot-submissions")) {
      return response(HttpStatus.UNPROCESSABLE_ENTITY, "MARKET_SNAPSHOT_INVALID", "市场快照参数无效", details,
          request);
    }
    return response(HttpStatus.BAD_REQUEST, "AUTH_CODE_INVALID", "请求参数无效", details, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiProblemResponse> handleMalformed(HttpMessageNotReadableException exception,
      HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, "AUTH_CODE_INVALID", "请求体无效", List.of(), request);
  }

  @ExceptionHandler(IdempotencyException.class)
  ResponseEntity<ApiProblemResponse> handleIdempotency(IdempotencyException exception,
      HttpServletRequest request) {
    return response(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), List.of(), request);
  }

  @ExceptionHandler(InsufficientBalanceException.class)
  ResponseEntity<ApiProblemResponse> handleInsufficientBalance(InsufficientBalanceException exception,
      HttpServletRequest request) {
    return response(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE", "账户可用余额不足", List.of(), request);
  }

  @ExceptionHandler(AccountDisableRejectedException.class)
  ResponseEntity<ApiProblemResponse> handleAccountDisableRejected(AccountDisableRejectedException exception,
      HttpServletRequest request) {
    return response(HttpStatus.CONFLICT, "ACCOUNT_DISABLE_CONFLICT", exception.getMessage(), List.of(), request);
  }

  @ExceptionHandler(AccountVersionConflictException.class)
  ResponseEntity<ApiProblemResponse> handleAccountVersionConflict(AccountVersionConflictException exception,
      HttpServletRequest request) {
    return response(HttpStatus.CONFLICT, "VERSION_CONFLICT", exception.getMessage(), List.of(), request);
  }

  @ExceptionHandler(InstrumentConflictException.class)
  ResponseEntity<ApiProblemResponse> handleInstrumentConflict(InstrumentConflictException exception,
      HttpServletRequest request) {
    return response(HttpStatus.CONFLICT, "INSTRUMENT_CONFLICT", "标的定义与既有事实冲突", List.of(), request);
  }

  @ExceptionHandler(MarketSnapshotValidationException.class)
  ResponseEntity<ApiProblemResponse> handleMarketSnapshotValidation(MarketSnapshotValidationException exception,
      HttpServletRequest request) {
    return response(HttpStatus.UNPROCESSABLE_ENTITY, "MARKET_SNAPSHOT_INVALID", exception.getMessage(), List.of(),
        request);
  }

  @ExceptionHandler(InsufficientPositionException.class)
  ResponseEntity<ApiProblemResponse> handleInsufficientPosition(InsufficientPositionException exception,
      HttpServletRequest request) {
    return response(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_POSITION", "可用持仓不足", List.of(), request);
  }

  @ExceptionHandler(ReplayInvariantViolationException.class)
  ResponseEntity<ApiProblemResponse> handleReplayInvariant(ReplayInvariantViolationException exception,
      HttpServletRequest request) {
    return response(HttpStatus.UNPROCESSABLE_ENTITY, "REPLAY_INVARIANT_VIOLATION",
        "历史重放会破坏余额或持仓不变量", List.of(), request);
  }

  @ExceptionHandler(CorporateActionNoOpenPositionException.class)
  ResponseEntity<ApiProblemResponse> handleCorporateActionNoPosition(CorporateActionNoOpenPositionException exception,
      HttpServletRequest request) {
    return response(HttpStatus.UNPROCESSABLE_ENTITY, "CORPORATE_ACTION_NO_OPEN_POSITION", exception.getMessage(),
        List.of(), request);
  }

  @ExceptionHandler(CorporateActionRatioException.class)
  ResponseEntity<ApiProblemResponse> handleCorporateActionRatio(CorporateActionRatioException exception,
      HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, "CORPORATE_ACTION_RATIO_INVALID", exception.getMessage(), List.of(),
        request);
  }

  @ExceptionHandler(CorporateActionUnsupportedException.class)
  ResponseEntity<ApiProblemResponse> handleCorporateActionUnsupported(CorporateActionUnsupportedException exception,
      HttpServletRequest request) {
    return response(HttpStatus.UNPROCESSABLE_ENTITY, "CORPORATE_ACTION_UNSUPPORTED", exception.getMessage(),
        List.of(), request);
  }

  @ExceptionHandler(CorrectionRejectedException.class)
  ResponseEntity<ApiProblemResponse> handleCorrectionRejected(CorrectionRejectedException exception,
      HttpServletRequest request) {
    return response(HttpStatus.CONFLICT, "CORRECTION_REJECTED", exception.getMessage(), List.of(), request);
  }

  @ExceptionHandler(PricePrecisionException.class)
  ResponseEntity<ApiProblemResponse> handlePricePrecision(PricePrecisionException exception,
      HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, "PRICE_PRECISION_INVALID", "数量与价格不能精确换算为分", List.of(), request);
  }

  @ExceptionHandler(TransactionFieldsException.class)
  ResponseEntity<ApiProblemResponse> handleTransactionFields(TransactionFieldsException exception,
      HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, "TRANSACTION_FIELDS_INVALID", "交易字段不符合当前类型", List.of(), request);
  }

  @ExceptionHandler(PortfolioResourceNotFoundException.class)
  ResponseEntity<ApiProblemResponse> handlePortfolioNotFound(PortfolioResourceNotFoundException exception,
      HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在或无权访问", List.of(), request);
  }

  @ExceptionHandler(StrategyRuleVersionConflictException.class)
  ResponseEntity<ApiProblemResponse> handleStrategyRuleConflict(StrategyRuleVersionConflictException exception,
      HttpServletRequest request) {
    return response(HttpStatus.CONFLICT, "RULE_VERSION_CONFLICT", exception.getMessage(), List.of(), request);
  }

  @ExceptionHandler(StrategyNotFoundException.class)
  ResponseEntity<ApiProblemResponse> handleStrategyNotFound(StrategyNotFoundException exception,
      HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND, "STRATEGY_NOT_FOUND", exception.getMessage(), List.of(), request);
  }

  @ExceptionHandler(StrategyValidationException.class)
  ResponseEntity<ApiProblemResponse> handleStrategyValidation(StrategyValidationException exception,
      HttpServletRequest request) {
    return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.code().name(), exception.getMessage(), List.of(),
        request);
  }

  @ExceptionHandler(StrategySeedRequiresEmptyLedgerException.class)
  ResponseEntity<ApiProblemResponse> handleStrategySeedRequiresEmptyLedger(
      StrategySeedRequiresEmptyLedgerException exception, HttpServletRequest request) {
    return response(HttpStatus.CONFLICT, "SEED_REQUIRES_EMPTY_LEDGER", exception.getMessage(), List.of(), request);
  }

  @ExceptionHandler(LedgerSnapshotRestoreRejectedException.class)
  ResponseEntity<ApiProblemResponse> handleSnapshotRestoreRejected(
      LedgerSnapshotRestoreRejectedException exception, HttpServletRequest request) {
    return response(HttpStatus.CONFLICT, "SNAPSHOT_RESTORE_REJECTED", exception.getMessage(), List.of(), request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiProblemResponse> handleBusinessValidation(IllegalArgumentException exception,
      HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, "LEDGER_COMMAND_INVALID", exception.getMessage(), List.of(), request);
  }

  private ResponseEntity<ApiProblemResponse> response(HttpStatus status, String code, String message,
      List<String> details, HttpServletRequest request) {
    String traceId = (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    return ResponseEntity.status(status).body(new ApiProblemResponse(code, message, traceId, details));
  }
}
