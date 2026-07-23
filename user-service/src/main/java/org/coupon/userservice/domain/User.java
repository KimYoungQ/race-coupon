package org.coupon.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 인증 대상 사용자. id가 JWT의 sub 클레임이 되고, 다운스트림 서비스가 토큰을 재검증해 이 값을 복원한다.
 * password는 반드시 BCrypt로 인코딩된 값만 담는다 — 평문을 넣지 않도록 create()가 인코딩된 값을 받는다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt 해시. 평문 금지. */
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private User(String username, String email, String encodedPassword, UserRole role) {
        this.username = username;
        this.email = email;
        this.password = encodedPassword;
        this.role = role;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * @param encodedPassword PasswordEncoder.encode()를 거친 값이어야 한다
     */
    public static User create(String username, String email, String encodedPassword, UserRole role) {
        return new User(username, email, encodedPassword, role);
    }
}
