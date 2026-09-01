package com.personal.investment.identity.interfaces;

import com.personal.investment.bootstrap.config.TraceIdFilter;
import com.personal.investment.identity.application.LoginService;
import com.personal.investment.identity.domain.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/auth/wechat")
public class AuthController {
  private final LoginService loginService;

  public AuthController(LoginService loginService) {
    this.loginService = loginService;
  }

  @PostMapping("/login")
  @ResponseStatus(HttpStatus.OK)
  public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    String traceId = (String) servletRequest.getAttribute(TraceIdFilter.ATTRIBUTE);
    var result = loginService.login(request.code(), request.bootstrapEnrollmentSecret(), traceId);
    return new LoginResponse(result.accessToken(), result.expiresAt(),
        new UserView(result.userId(), result.role()));
  }

  public record LoginRequest(@NotBlank @Size(max = 256) String code,
      @Size(max = 512) String bootstrapEnrollmentSecret) {
  }

  public record LoginResponse(String accessToken, Instant expiresAt, UserView user) {
  }

  public record UserView(String userId, Role role) {
  }
}
