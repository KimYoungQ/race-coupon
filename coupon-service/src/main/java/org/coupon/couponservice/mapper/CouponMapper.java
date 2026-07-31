package org.coupon.couponservice.mapper;

import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.dto.CouponResponse;
import org.coupon.couponservice.dto.IssuableCouponResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Coupon Entity → Response DTO 변환. 구현체는 컴파일 시점에 생성된다.
 * Entity 생성은 도메인 불변식을 지키는 {@link Coupon}의 빌더가 담당하므로 여기서 다루지 않는다.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CouponMapper {


    @Mapping(target = "couponId", source = "coupon.id")
    @Mapping(target = "issuedQuantity", source = "issued")
    @Mapping(target = "remaining", expression = "java(coupon.getTotalQuantity() - issued)")
    CouponIssueResponse toIssueResponse(Coupon coupon, long issued);


    /*
    V1·V2 전용. issuedQuantity가 발급의 권위였던 시절, 발급 직후의 엔티티를 그대로 변환했다.
    V4에서는 발급 수가 Redis에 있고 엔티티는 컨슈머 랙만큼 뒤처지므로 위의 2인자 버전을 쓴다.

    @Mapping(target = "couponId", source = "id")
    @Mapping(target = "remaining", expression = "java(coupon.remaining())")
    CouponIssueResponse toIssueResponse(Coupon coupon);
    */

    @Mapping(target = "couponId", source = "id")
    CouponResponse toResponse(Coupon coupon);

    @Mapping(target = "couponId", source = "id")
    @Mapping(target = "remaining", expression = "java(coupon.remaining())")
    IssuableCouponResponse toIssuableResponse(Coupon coupon);

    List<IssuableCouponResponse> toIssuableResponses(List<Coupon> coupons);
}
