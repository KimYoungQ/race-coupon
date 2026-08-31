package org.coupon.couponservice.domain;

import org.coupon.couponservice.exception.InvalidDiscountException;

public enum DiscountType {

    PERCENT {
        @Override
        public void validate(long value) {
            if (value < 1 || value > 100) {
                throw new InvalidDiscountException();
            }
        }
    },

    FIXED_AMOUNT {
        @Override
        public void validate(long value) {
            if (value <= 0) {
                throw new InvalidDiscountException();
            }
        }
    };

    public abstract void validate(long value);
}
