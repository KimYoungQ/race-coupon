package org.coupon.couponservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.dto.CouponCreateRequest;
import org.coupon.couponservice.dto.CouponResponse;
import org.coupon.couponservice.mapper.CouponMapper;
import org.coupon.couponservice.repository.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponAdminService {

    private final CouponRepository couponRepository;
    private final CouponMapper couponMapper;

    @Transactional
    public CouponResponse create(CouponCreateRequest request) {
        Coupon coupon = Coupon.builder()
                .title(request.title())
                .totalQuantity(request.totalQuantity())
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .maxDiscountAmount(request.maxDiscountAmount())
                .minOrderAmount(request.minOrderAmount())
                .eventEndAt(request.eventEndAt())
                .build();

        Coupon saved = couponRepository.save(coupon);
        log.info("쿠폰 등록 완료: couponId={}, title={}, totalQuantity={}",
                saved.getId(), saved.getTitle(), saved.getTotalQuantity());
        return couponMapper.toResponse(saved);
    }
}
