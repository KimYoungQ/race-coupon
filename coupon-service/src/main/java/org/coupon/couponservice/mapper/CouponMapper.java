package org.coupon.couponservice.mapper;

import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.dto.CouponResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Coupon Entity → Response DTO 변환. 구현체는 컴파일 시점에 생성된다.
 * Entity 생성은 도메인 불변식을 지키는 {@link Coupon#create}가 담당하므로 여기서 다루지 않는다.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CouponMapper {

    /**
     * remaining은 필드가 아니라 도메인 계산값이라 getter 규약에 잡히지 않아 직접 호출한다.
     */
    @Mapping(target = "couponId", source = "id")
    @Mapping(target = "remaining", expression = "java(coupon.remaining())")
    CouponIssueResponse toIssueResponse(Coupon coupon);

    @Mapping(target = "couponId", source = "id")
    CouponResponse toResponse(Coupon coupon);
}
