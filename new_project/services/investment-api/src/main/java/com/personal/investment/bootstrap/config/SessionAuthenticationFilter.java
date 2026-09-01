package com.personal.investment.bootstrap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.identity.application.AuthException;
import com.personal.investment.identity.infrastructure.RedisSessionStore;
import com.personal.investment.identity.interfaces.ApiProblemResponse;
import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
  private final RedisSessionStore sessionStore;
  private final ObjectMapper objectMapper;

  public SessionAuthenticationFilter(RedisSessionStore sessionStore, ObjectMapper objectMapper) {
    this.sessionStore = sessionStore;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return path.equals("/api/v1/auth/wechat/login") || path.startsWith("/actuator/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    try {
      String token = bearerToken(request);
      var session = sessionStore.find(token)
          .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "会话已过期"));
      var principal = SessionAuthenticationPrincipal.from(session);
      var authentication = new UsernamePasswordAuthenticationToken(principal, null,
          List.of(new SimpleGrantedAuthority("ROLE_" + principal.user().role().name())));
      SecurityContextHolder.getContext().setAuthentication(authentication);
      filterChain.doFilter(request, response);
    } catch (AuthException exception) {
      SecurityContextHolder.clearContext();
      writeProblem(request, response, exception.status(), exception.code(), exception.getMessage());
    }
  }

  private String bearerToken(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7) {
      throw new AuthException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "会话格式无效");
    }
    return authorization.substring(7);
  }

  private void writeProblem(HttpServletRequest request, HttpServletResponse response,
      HttpStatus status, String code, String message) throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    String traceId = (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    objectMapper.writeValue(response.getOutputStream(),
        new ApiProblemResponse(code, message, traceId, List.of()));
  }
}
