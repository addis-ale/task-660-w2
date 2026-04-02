package com.heritage.marketplace.order;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByStatusAndReservationExpiresAtBefore(OrderStatus status, LocalDateTime threshold);

    Page<Order> findByMember_IdOrderByCreatedAtDesc(UUID memberId, Pageable pageable);

    Page<Order> findByMember_IdAndStatusOrderByCreatedAtDesc(UUID memberId, OrderStatus status, Pageable pageable);

    Optional<Order> findByIdAndMember_Id(UUID orderId, UUID memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> lockById(@Param("orderId") UUID orderId);
}
