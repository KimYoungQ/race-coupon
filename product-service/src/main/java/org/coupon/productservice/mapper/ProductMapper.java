package org.coupon.productservice.mapper;

import org.coupon.productservice.domain.Product;
import org.coupon.productservice.dto.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper {

    @Mapping(target = "productId", source = "id")
    ProductResponse toResponse(Product product);

    List<ProductResponse> toResponses(List<Product> products);
}
