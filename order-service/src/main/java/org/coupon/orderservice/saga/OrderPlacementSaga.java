package org.coupon.orderservice.saga;

import org.coupon.orderservice.saga.framework.SagaContext;
import org.coupon.orderservice.saga.framework.SagaDefinition;
import org.coupon.orderservice.saga.framework.SagaState;

@SagaDefinition(type = OrderPlacementSaga.TYPE,
        stepIds = {AbstractOrderSaga.STOCK_RESERVATION, AbstractOrderSaga.COUPON_APPLY})
public class OrderPlacementSaga extends AbstractOrderSaga {

    public static final String TYPE = "order-placement";

    public OrderPlacementSaga(SagaState state, SagaContext context) {
        super(state, context);
    }
}
