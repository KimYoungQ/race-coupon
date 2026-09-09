package org.coupon.orderservice.client;

import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.coupon.orderservice.support.MySqlTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MySqlTestContainer.class)
class CouponCheckerTest {

    private static final long COUPON_ID = 9L;
    private static final String CIRCUIT_NAME = "coupon-service";

    @Autowired
    private CouponChecker couponChecker;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    // 쿠폰 서비스 HTTP 호출은 Mock 으로 대체한다 (기본값 = 통과)
    @MockitoBean
    private CouponClient couponClient;

    @BeforeEach
    @AfterEach
    void resetCircuit() {
        // 서킷은 스프링 컨텍스트에 하나뿐이라 테스트끼리(다른 테스트 클래스와도) 공유된다.
        // 앞 테스트가 열어 둔 서킷이 새지 않도록 시작 전과 끝난 뒤 모두 닫는다
        circuitBreakerRegistry.circuitBreaker(CIRCUIT_NAME).reset();
    }

    @Test
    @DisplayName("쿠폰 서비스가 정상이면 예외 없이 통과한다")
    void passWhenCouponServiceIsHealthy() {
        // given
        doNothing().when(couponClient).checkUsable(COUPON_ID);

        // when
        // then
        assertThatCode(() -> couponChecker.checkUsable(COUPON_ID)).doesNotThrowAnyException();
        verify(couponClient).checkUsable(COUPON_ID);
        assertThat(circuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("쿠폰 서비스가 응답하지 못하면 COUPON_SERVICE_UNAVAILABLE 로 즉시 거절한다")
    void rejectWhenCouponServiceDoesNotRespond() {
        // given
        doThrow(retryableException()).when(couponClient).checkUsable(COUPON_ID);

        // when
        Throwable thrown = catchThrowable(() -> couponChecker.checkUsable(COUPON_ID));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.COUPON_SERVICE_UNAVAILABLE);
        verify(couponClient).checkUsable(COUPON_ID);
    }

    @Test
    @DisplayName("5번 연속 응답 없음이면 서킷이 열려 6번째는 쿠폰 서비스를 부르지 않는다")
    void openCircuitAfterFiveConsecutiveFailures() {
        // given
        doThrow(retryableException()).when(couponClient).checkUsable(COUPON_ID);
        for (int i = 0; i < 5; i++) {
            catchThrowable(() -> couponChecker.checkUsable(COUPON_ID));
        }

        // when
        Throwable thrown = catchThrowable(() -> couponChecker.checkUsable(COUPON_ID));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.COUPON_SERVICE_UNAVAILABLE);
        verify(couponClient, times(5)).checkUsable(COUPON_ID);
        assertThat(circuitState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("쿠폰 서비스가 멀쩡히 거절한 4xx 는 실패로 세지 않아 서킷이 열리지 않는다")
    void ignoreBusinessRejection() {
        // given
        doThrow(new BusinessException(ErrorCode.COUPON_ALREADY_USED)).when(couponClient).checkUsable(COUPON_ID);

        // when
        for (int i = 0; i < 6; i++) {
            Throwable thrown = catchThrowable(() -> couponChecker.checkUsable(COUPON_ID));

            // then
            assertThat(thrown).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.COUPON_ALREADY_USED);
        }
        verify(couponClient, times(6)).checkUsable(COUPON_ID);
        assertThat(circuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("열린 서킷이 half-open 이 되면 다음 호출은 다시 쿠폰 서비스까지 간다")
    void reachCouponServiceAgainWhenHalfOpen() {
        // given
        doThrow(retryableException()).when(couponClient).checkUsable(COUPON_ID);
        for (int i = 0; i < 5; i++) {
            catchThrowable(() -> couponChecker.checkUsable(COUPON_ID));
        }
        assertThat(circuitState()).isEqualTo(CircuitBreaker.State.OPEN);
        // 열림 유지 10초를 기다리는 대신 half-open 으로 강제 전환한다
        circuitBreakerRegistry.circuitBreaker(CIRCUIT_NAME).transitionToHalfOpenState();
        doNothing().when(couponClient).checkUsable(COUPON_ID);

        // when
        // then
        assertThatCode(() -> couponChecker.checkUsable(COUPON_ID)).doesNotThrowAnyException();
        verify(couponClient, times(6)).checkUsable(COUPON_ID);
    }

    private CircuitBreaker.State circuitState() {
        return circuitBreakerRegistry.circuitBreaker(CIRCUIT_NAME).getState();
    }

    // 연결 실패·타임아웃 때 Feign 이 던지는 예외 (status -1 = HTTP 응답 자체가 없었다는 뜻)
    private static RetryableException retryableException() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/coupons/" + COUPON_ID + "/usable",
                Map.of(), Request.Body.empty(), null);
        return new RetryableException(-1, "connect timed out", Request.HttpMethod.GET, (Long) null, request);
    }
}
