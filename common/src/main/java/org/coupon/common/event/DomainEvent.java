package org.coupon.common.event;

public interface DomainEvent {

    String aggregateType();

    String aggregateId();

    String type();

    Object payload();
}
