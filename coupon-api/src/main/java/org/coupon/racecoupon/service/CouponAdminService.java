package org.coupon.racecoupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.racecoupon.domain.Coupon;
import org.coupon.racecoupon.dto.CouponCreateRequest;
import org.coupon.racecoupon.dto.CouponResponse;
import org.coupon.racecoupon.repository.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 전용 쿠폰 등록. 발급(동시성 제어 대상)과 달리 경합이 없어 잠금 전략이 필요 없다.
 *
 * <p>접근 제어는 이 클래스가 아니라 컨트롤러의 {@code @PreAuthorize}가 선언한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponAdminService {

    private final CouponRepository couponRepository;

    /**
     * 할인 값 검증은 {@link Coupon#create}가 도메인 불변식으로 처리한다.
     * 범위를 벗어나면 InvalidDiscountException이 나고 전역 핸들러가 400으로 변환한다.
     */
    @Transactional
    public CouponResponse create(CouponCreateRequest request) {
        Coupon coupon = Coupon.create(
                request.title(),
                request.totalQuantity(),
                request.discountType(),
                request.discountValue(),
                request.maxDiscountAmount(),
                request.minOrderAmount());

        Coupon saved = couponRepository.save(coupon);
        log.info("쿠폰 등록 완료: couponId={}, title={}, totalQuantity={}",
                saved.getId(), saved.getTitle(), saved.getTotalQuantity());
        return CouponResponse.from(saved);
    }
}
