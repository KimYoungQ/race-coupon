package org.coupon.orderservice.saga;

import org.coupon.orderservice.saga.framework.SagaContext;
import org.coupon.orderservice.saga.framework.SagaDefinition;
import org.coupon.orderservice.saga.framework.SagaState;

@SagaDefinition(type = StockOnlyOrderSaga.TYPE, stepIds = {AbstractOrderSaga.STOCK_RESERVATION})
public class StockOnlyOrderSaga extends AbstractOrderSaga {

    public static final String TYPE = "order-stock-only";

    public StockOnlyOrderSaga(SagaState state, SagaContext context) {
        super(state, context);
    }
}
