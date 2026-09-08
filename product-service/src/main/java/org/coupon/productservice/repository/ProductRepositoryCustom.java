package org.coupon.productservice.repository;

public interface ProductRepositoryCustom {

    long decreaseStock(Long productId, long quantity);

    long increaseStock(Long productId, long quantity);

}
