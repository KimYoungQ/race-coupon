package org.coupon.orderservice.saga;

import org.coupon.common.event.AggregateTypes;
import org.coupon.common.event.CouponApplyRequestPayload;
import org.coupon.common.event.RequestType;
import org.coupon.common.event.StockReservationRequestPayload;
import org.coupon.orderservice.saga.framework.AbstractSaga;
import org.coupon.orderservice.saga.framework.SagaContext;
import org.coupon.orderservice.saga.framework.SagaState;
import org.coupon.orderservice.saga.framework.SagaStepMessage;

public abstract class AbstractOrderSaga extends AbstractSaga {

    public static final String STOCK_RESERVATION = AggregateTypes.STOCK_RESERVATION;
    public static final String COUPON_APPLY = AggregateTypes.COUPON_APPLY;

    protected AbstractOrderSaga(SagaState state, SagaContext context) {
        super(state, context);
    }

    public OrderSagaPayload payload() {
        return getPayload(OrderSagaPayload.class);
    }

    public void fixOrderAmount(long orderAmount) {
        updatePayload(payload().withOrderAmount(orderAmount));
    }

    @Override
    protected SagaStepMessage stepMessage(String stepId) {
        return message(stepId, RequestType.REQUEST);
    }

    @Override
    protected SagaStepMessage compensatingStepMessage(String stepId) {
        return message(stepId, RequestType.CANCEL);
    }

    private SagaStepMessage message(String stepId, RequestType type) {
        OrderSagaPayload payload = payload();
        return switch (stepId) {
            case STOCK_RESERVATION -> new SagaStepMessage(STOCK_RESERVATION, type.name(),
                    new StockReservationRequestPayload(
                            payload.orderId(), payload.productId(), payload.quantity(), type));
            case COUPON_APPLY -> {
                if (payload.orderAmount() == null) {
                    throw new IllegalStateException(
                            "재고 응답 전에는 쿠폰 요청을 만들 수 없다: orderId=" + payload.orderId());
                }
                yield new SagaStepMessage(COUPON_APPLY, type.name(),
                        new CouponApplyRequestPayload(
                                payload.orderId(), payload.userId(), payload.couponId(),
                                payload.orderAmount(), type));
            }
            default -> throw new IllegalArgumentException("주문 사가에 없는 스텝: " + stepId);
        };
    }
}
