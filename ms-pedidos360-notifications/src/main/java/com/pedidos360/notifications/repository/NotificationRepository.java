package com.pedidos360.notifications.repository;

import com.pedidos360.notifications.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByEventId(String eventId);

    List<Notification> findAllByOrderByCreatedAtDesc();

    List<Notification> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
