package org.coupon.couponservice.service;

import lombok.RequiredArgsConstructor;
import org.coupon.couponservice.domain.IssuedCouponStatus;
import org.coupon.couponservice.dto.IssuableCouponResponse;
import org.coupon.couponservice.exception.CouponAlreadyUsedException;
import org.coupon.couponservice.exception.CouponNotFoundException;
import org.coupon.couponservice.exception.CouponNotIssuedYetException;
import org.coupon.couponservice.mapper.CouponMapper;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponMapper couponMapper;

    @Transactional(readOnly = true)
    public List<IssuableCouponResponse> findIssuable(Long userId) {
        return couponMapper.toIssuableResponses(
                couponRepository.findIssuableBy(userId, LocalDateTime.now()));
    }
    
    @Transactional(readOnly = true)
    public void checkUsable(Long userId, Long couponId) {
        if (!couponRepository.existsById(couponId)) {
            throw new CouponNotFoundException(couponId);
        }

        IssuedCouponStatus status = issuedCouponRepository.findStatusBy(userId, couponId);
        if (status == null) {
            throw new CouponNotIssuedYetException(userId, couponId);
        }
        if (status == IssuedCouponStatus.USED) {
            throw new CouponAlreadyUsedException(userId, couponId);
        }
    }
}
