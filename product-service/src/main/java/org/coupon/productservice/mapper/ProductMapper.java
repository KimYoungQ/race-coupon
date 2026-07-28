package org.coupon.productservice.mapper;

import org.coupon.productservice.domain.Product;
import org.coupon.productservice.dto.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Product Entity → Response DTO 변환. 구현체는 컴파일 시점에 생성된다.
 * Entity 생성은 도메인 불변식을 지키는 {@link Product}의 빌더가 담당하므로 여기서 다루지 않는다.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper {

    @Mapping(target = "productId", source = "id")
    ProductResponse toResponse(Product product);

    List<ProductResponse> toResponses(List<Product> products);
}
