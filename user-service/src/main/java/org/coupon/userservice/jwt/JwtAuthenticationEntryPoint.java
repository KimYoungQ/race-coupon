package org.coupon.userservice.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.racecoupon.common.ApiResponse;
import org.coupon.racecoupon.exception.ErrorCode;
import org.coupon.userservice.exception.ExpiredTokenException;
import org.coupon.userservice.exception.InvalidTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 요청의 401 응답을 만든다.
 * {@link JwtAuthenticationFilter}가 요청 속성에 남긴 실패 원인을 읽어
 * 만료(재발급 유도)와 위조(거부)를 클라이언트가 구분할 수 있게 한다.
 */
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
            // 토큰을 아예 보내지 않았거나 보호된 경로에 익명으로 접근한 경우.
            code = UNAUTHORIZED_CODE;
            message = UNAUTHORIZED_MESSAGE;
        }

        log.debug("인증 실패 응답: uri={}, errorCode={}", request.getRequestURI(), code);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // EntryPoint는 DispatcherServlet 이전이라 HttpMessageConverter를 쓸 수 없어 직접 직렬화한다.
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, message)));
    }
}
