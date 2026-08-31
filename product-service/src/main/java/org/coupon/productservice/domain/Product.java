package org.coupon.productservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.productservice.exception.ProductOutOfStockException;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Long stock;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Product(String name, Long price, Long stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.createdAt = LocalDateTime.now();
    }

    public void decrease(long quantity) {
        if (stock < quantity) {
            throw new ProductOutOfStockException(id, stock, quantity);
        }
        this.stock -= quantity;
    }

    public void restore(long quantity) {
        this.stock += quantity;
    }
}
