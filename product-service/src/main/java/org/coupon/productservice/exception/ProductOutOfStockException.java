package org.coupon.productservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class ProductOutOfStockException extends BusinessException {

    public ProductOutOfStockException(Long productId, long stock, long requested) {
        super(ErrorCode.PRODUCT_OUT_OF_STOCK,
                "상품 재고가 부족합니다: productId=" + productId + ", 재고=" + stock + ", 요청=" + requested);
    }
}
