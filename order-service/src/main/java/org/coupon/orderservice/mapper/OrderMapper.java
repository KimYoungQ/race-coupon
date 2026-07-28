package org.coupon.orderservice.mapper;

import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.OrderItem;
import org.coupon.orderservice.dto.OrderCreateResponse;
import org.coupon.orderservice.dto.OrderItemResponse;
import org.coupon.orderservice.dto.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Order Entity → Response DTO 변환. 구현체는 컴파일 시점에 생성된다.
 * Entity 생성은 상태·불변식을 지키는 {@link Order}의 빌더가 담당하므로 여기서 다루지 않는다.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    @Mapping(target = "orderId", source = "id")
    OrderCreateResponse toCreateResponse(Order order);

    @Mapping(target = "orderId", source = "id")
    OrderResponse toResponse(Order order);

    OrderItemResponse toItemResponse(OrderItem item);
}
