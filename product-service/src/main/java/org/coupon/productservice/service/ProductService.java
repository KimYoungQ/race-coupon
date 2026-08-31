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
