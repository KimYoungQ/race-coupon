package org.coupon.orderservice.mapper;

import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.OrderItem;
import org.coupon.orderservice.dto.OrderCreateResponse;
import org.coupon.orderservice.dto.OrderItemResponse;
import org.coupon.orderservice.dto.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    @Mapping(target = "orderId", source = "id")
    OrderCreateResponse toCreateResponse(Order order);

    @Mapping(target = "orderId", source = "id")
    OrderResponse toResponse(Order order);

    OrderItemResponse toItemResponse(OrderItem item);
}
