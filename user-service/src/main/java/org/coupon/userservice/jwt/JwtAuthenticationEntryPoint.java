package org.coupon.userservice.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.response.ApiResponse;
import org.coupon.common.exception.ErrorCode;
import org.coupon.userservice.exception.ExpiredTokenException;
import org.coupon.userservice.exception.InvalidTokenException;
import org.springframework.http.HttpStatus;
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

    private static final String EXCEPTION_ATTRIBUTE = "exception";
    private static final String UNAUTHORIZED_CODE = "UNAUTHORIZED";
    private static final String UNAUTHORIZED_MESSAGE = "인증이 필요합니다";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Object cause = request.getAttribute(EXCEPTION_ATTRIBUTE);

        String code;
        String message;
        if (cause instanceof ExpiredTokenException) {
            code = ErrorCode.TOKEN_EXPIRED.getCode();
            message = ErrorCode.TOKEN_EXPIRED.getMessage();
        } else if (cause instanceof InvalidTokenException) {
            code = ErrorCode.INVALID_TOKEN.getCode();
            message = ErrorCode.INVALID_TOKEN.getMessage();
        } else {
            code = UNAUTHORIZED_CODE;
            message = UNAUTHORIZED_MESSAGE;
        }

        log.debug("인증 실패 응답: uri={}, errorCode={}", request.getRequestURI(), code);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, message)));
    }
}
