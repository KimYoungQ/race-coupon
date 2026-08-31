package org.coupon.couponservice.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.response.ApiResponse;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Object cause = request.getAttribute(JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE);

        String errorCode;
        String errorMessage;
        if (cause instanceof BusinessException businessException) {
            errorCode = businessException.getErrorCode().getCode();
            errorMessage = businessException.getMessage();
        } else {
            errorCode = ErrorCode.UNAUTHORIZED.getCode();
            errorMessage = ErrorCode.UNAUTHORIZED.getMessage();
        }

        log.debug("인증 실패 응답: uri={}, errorCode={}", request.getRequestURI(), errorCode);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(errorCode, errorMessage)));
    }
}
