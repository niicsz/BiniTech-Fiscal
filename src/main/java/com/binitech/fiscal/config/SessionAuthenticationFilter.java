package com.binitech.fiscal.config;

import com.binitech.fiscal.application.ports.outbound.AuthenticationPort;
import com.binitech.fiscal.domain.SessionIdentity;
import com.binitech.fiscal.domain.exception.ExternalServiceUnavailableException;
import com.binitech.fiscal.domain.exception.TenantRequiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAuthenticationFilter extends OncePerRequestFilter {

  private final AuthenticationPort authentication;

  public SessionAuthenticationFilter(AuthenticationPort authentication) {
    this.authentication = authentication;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        SessionIdentity identity = authentication.session(header.substring(7));
        var token =
            new UsernamePasswordAuthenticationToken(
                identity, null, List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED")));
        SecurityContextHolder.getContext().setAuthentication(token);
      } catch (TenantRequiredException e) {
        SecurityContextHolder.clearContext();
        response.setStatus(403);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + e.getMessage() + "\"}");
        return;
      } catch (ExternalServiceUnavailableException e) {
        SecurityContextHolder.clearContext();
        response.setStatus(503);
        return;
      } catch (RuntimeException e) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
