package org.coupon.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.productservice.domain.Product;
import org.coupon.productservice.dto.ProductCreateRequest;
import org.coupon.productservice.dto.ProductResponse;
import org.coupon.productservice.exception.ProductNotFoundException;
import org.coupon.productservice.mapper.ProductMapper;
import org.coupon.productservice.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 등록·조회. 재고 차감은 여기 없다 —
 * {@code PRODUCT.stock}을 바꾸는 경로는 주문 Saga의 Kafka 컨슈머 하나뿐이다.
 *
 * <p>접근 제어는 이 클래스가 아니라 컨트롤러의 {@code @PreAuthorize}가 선언한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Product product = Product.builder()
                .name(request.name())
                .price(request.price())
                .stock(request.stock())
                .build();

        Product saved = productRepository.save(product);
        log.info("상품 등록 완료: productId={}, name={}, price={}, stock={}",
                saved.getId(), saved.getName(), saved.getPrice(), saved.getStock());
        return productMapper.toResponse(saved);
    }

    /**
     * open-in-view가 꺼져 있어 트랜잭션 밖에서는 LAZY 접근이 실패한다.
     * DTO 변환을 이 경계 안에서 끝내는 이유다.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return productMapper.toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts() {
        return productMapper.toResponses(productRepository.findAll());
    }
}
