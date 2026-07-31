package org.coupon.couponservice.service;

import lombok.RequiredArgsConstructor;
import org.coupon.couponservice.dto.IssuableCouponResponse;
import org.coupon.couponservice.mapper.CouponMapper;
import org.coupon.couponservice.repository.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponMapper couponMapper;

    @Transactional(readOnly = true)
    public List<IssuableCouponResponse> findIssuable(Long userId) {
        return couponMapper.toIssuableResponses(
                couponRepository.findIssuableBy(userId, LocalDateTime.now()));
    }
}
