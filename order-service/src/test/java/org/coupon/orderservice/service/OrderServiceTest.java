package org.coupon.orderservice.service;

import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.coupon.orderservice.client.CouponClient;
import org.coupon.orderservice.domain.OrderStatus;
import org.coupon.orderservice.dto.OrderCreateRequest;
import org.coupon.orderservice.dto.OrderCreateResponse;
import org.coupon.orderservice.repository.OrderItemRepository;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.saga.OrderPlacementSaga;
import org.coupon.orderservice.saga.framework.SagaStateRepository;
import org.coupon.orderservice.support.MySqlTestContainer;
import org.coupon.sagapersistence.outbox.OutboxEventRepository;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MySqlTestContainer.class)
class OrderServiceTest {

    private static final long USER_ID = 42L;
    private static final long PRODUCT_ID = 7L;
    private static final long COUPON_ID = 9L;
    private static final int QUANTITY = 2;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private SagaStateRepository sagaStateRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    // 쿠폰 사전 검증은 쿠폰 서비스 HTTP 호출이므로 Mock 으로 대체한다 (기본값 = 통과)
    @MockitoBean
    private CouponClient couponClient;

    @BeforeEach
    void resetCircuit() {
        // CouponChecker 의 서킷은 스프링 컨텍스트에 하나뿐이라 테스트끼리 공유된다. 앞 테스트의 실패 기록이 새지 않도록 매번 닫는다
        circuitBreakerRegistry.circuitBreaker("coupon-service").reset();
    }

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAllInBatch();
        sagaStateRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("쿠폰 확인을 통과하면 주문이 CREATED 로 저장되고 쿠폰 포함 사가가 시작된다")
    void createWithValidCoupon() {
        // given
        OrderCreateRequest request = new OrderCreateRequest(PRODUCT_ID, QUANTITY, COUPON_ID);

        // when
        OrderCreateResponse response = orderService.create(USER_ID, request);

        // then
        verify(couponClient).checkUsable(COUPON_ID);
        assertThat(orderRepository.findById(response.orderId()))
                .hasValueSatisfying(order -> {
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
                    assertThat(order.getCouponId()).isEqualTo(COUPON_ID);
                });
        assertThat(sagaStateRepository.findByOrderId(response.orderId()))
                .hasValueSatisfying(saga -> assertThat(saga.getType()).isEqualTo(OrderPlacementSaga.TYPE));
    }

    @Test
    @DisplayName("쿠폰 서비스가 거절하면 같은 에러 코드로 실패하고 주문은 만들어지지 않는다")
    void createWithInvalidCoupon() {
        // given
        doThrow(new BusinessException(ErrorCode.COUPON_NOT_OWNED))
                .when(couponClient).checkUsable(COUPON_ID);
        OrderCreateRequest request = new OrderCreateRequest(PRODUCT_ID, QUANTITY, COUPON_ID);

        // when
        Throwable thrown = catchThrowable(() -> orderService.create(USER_ID, request));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_OWNED);
        assertThat(orderRepository.count()).isZero();
        assertThat(sagaStateRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("쿠폰 서비스가 응답하지 못하면 503 으로 거절되고 주문은 만들어지지 않는다")
    void createWhenCouponServiceDoesNotRespond() {
        // given
        doThrow(retryableException()).when(couponClient).checkUsable(COUPON_ID);
        OrderCreateRequest request = new OrderCreateRequest(PRODUCT_ID, QUANTITY, COUPON_ID);

        // when
        Throwable thrown = catchThrowable(() -> orderService.create(USER_ID, request));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.COUPON_SERVICE_UNAVAILABLE);
        assertThat(orderRepository.count()).isZero();
        assertThat(sagaStateRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("쿠폰 없이 주문하면 쿠폰 서비스를 호출하지 않는다")
    void createWithoutCoupon() {
        // given
        OrderCreateRequest request = new OrderCreateRequest(PRODUCT_ID, QUANTITY, null);

        // when
        OrderCreateResponse response = orderService.create(USER_ID, request);

        // then
        verify(couponClient, never()).checkUsable(anyLong());
        assertThat(orderRepository.findById(response.orderId())).isPresent();
    }

    // 연결 실패·타임아웃 때 Feign 이 던지는 예외 (status -1 = HTTP 응답 자체가 없었다는 뜻)
    private static RetryableException retryableException() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/coupons/" + COUPON_ID + "/usable",
                Map.of(), Request.Body.empty(), null);
        return new RetryableException(-1, "connect timed out", Request.HttpMethod.GET, (Long) null, request);
    }
}
