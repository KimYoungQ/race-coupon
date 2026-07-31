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
    private final CouponMapper couponMapper;

    /**
     * 할인 값 검증은 {@link Coupon}의 빌더가 도메인 불변식으로 처리한다.
     * 범위를 벗어나면 InvalidDiscountException이 나고 전역 핸들러가 400으로 변환한다.
     */
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
