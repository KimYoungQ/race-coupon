package org.coupon.couponservice.mapper;

import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.dto.CouponResponse;
import org.coupon.couponservice.dto.IssuableCouponResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CouponMapper {

    @Mapping(target = "couponId", source = "coupon.id")
    @Mapping(target = "issuedQuantity", source = "issued")
    @Mapping(target = "remaining", expression = "java(coupon.getTotalQuantity() - issued)")
    CouponIssueResponse toIssueResponse(Coupon coupon, long issued);

    @Mapping(target = "couponId", source = "id")
    CouponResponse toResponse(Coupon coupon);

    @Mapping(target = "couponId", source = "id")
    @Mapping(target = "remaining", expression = "java(coupon.remaining())")
    IssuableCouponResponse toIssuableResponse(Coupon coupon);

    List<IssuableCouponResponse> toIssuableResponses(List<Coupon> coupons);
}
