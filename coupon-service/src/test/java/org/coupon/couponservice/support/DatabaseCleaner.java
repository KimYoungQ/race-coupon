package org.coupon.couponservice.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@TestComponent
public class DatabaseCleaner {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void clear() {
        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        tableNames().forEach(t -> em.createNativeQuery("TRUNCATE TABLE " + t).executeUpdate());
        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<String> tableNames() {
        return em.createNativeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")
                .getResultList();
    }
}
