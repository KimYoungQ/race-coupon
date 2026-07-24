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

/**
 * 인증되지 않은 요청의 401 응답을 만든다.
 *
 * <p>이 빈이 없으면 Spring Security 기본값인 {@code Http403ForbiddenEntryPoint}가 쓰여
 * 토큰 없음·만료·위조가 전부 <b>빈 본문 403</b>으로 뭉개진다.
 *
 * <p>{@link JwtAuthenticationFilter}가 요청 속성에 남긴 실패 원인을 읽어
 * 만료(재발급 유도)와 위조(재로그인)를 클라이언트가 구분할 수 있게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // 필터가 실패 원인을 BusinessException으로 통일해 남기므로 ErrorCode만 꺼내면
        // 만료/위조/토큰없음 3분기가 그대로 나온다. 예외 타입별 instanceof 분기가 필요 없다.
        Object cause = request.getAttribute(JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE);

        String errorCode;
        String errorMessage;
        if (cause instanceof BusinessException businessException) {
            errorCode = businessException.getErrorCode().getCode();
            errorMessage = businessException.getMessage();
        } else {
            // 토큰을 아예 보내지 않았거나 보호된 경로에 익명으로 접근한 경우.
            errorCode = ErrorCode.UNAUTHORIZED.getCode();
            errorMessage = ErrorCode.UNAUTHORIZED.getMessage();
        }

        // error가 아니라 debug다. 인증 실패는 클라이언트 잘못이고, 이 서비스는 부하 측정 대상이라
        // 토큰 없는 요청이 쏟아지면 로그 기록 자체가 처리량 수치를 왜곡한다.
        log.debug("인증 실패 응답: uri={}, errorCode={}", request.getRequestURI(), errorCode);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // EntryPoint는 DispatcherServlet 이전이라 HttpMessageConverter를 쓸 수 없어 직접 직렬화한다.
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(errorCode, errorMessage)));
    }
}
