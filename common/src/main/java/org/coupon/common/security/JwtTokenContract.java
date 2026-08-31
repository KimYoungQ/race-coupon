package org.coupon.common.security;

public final class JwtTokenContract {

    public static final String CLAIM_ROLE = "role";

    public static final String CLAIM_USERNAME = "username";

    public static final String CLAIM_TYPE = "type";

    public static final String TYPE_ACCESS = "access";

    public static final String ROLE_PREFIX = "ROLE_";

    private JwtTokenContract() {
    }
}
