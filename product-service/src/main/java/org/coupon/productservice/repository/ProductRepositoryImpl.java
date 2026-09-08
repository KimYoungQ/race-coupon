package org.coupon.productservice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import static org.coupon.productservice.domain.QProduct.product;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public long decreaseStock(Long productId, long quantity) {
        return queryFactory
                .update(product)
                .set(product.stock, product.stock.subtract(quantity))
                .where(
                        product.id.eq(productId),
                        product.stock.goe(quantity))
                .execute();
    }

    public long increaseStock(Long productId, long quantity) {
        return queryFactory
                .update(product)
                .set(product.stock, product.stock.add(quantity))
                .where(product.id.eq(productId))
                .execute();
    }
}
