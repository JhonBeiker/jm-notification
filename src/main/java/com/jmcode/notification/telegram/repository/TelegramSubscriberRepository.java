package com.jmcode.notification.telegram.repository;

import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramSubscriberRepository extends JpaRepository<TelegramSubscriber, Long> {

    Optional<TelegramSubscriber> findByChatId(Long chatId);

    Optional<TelegramSubscriber> findByExternalUserIdAndActiveTrue(String externalUserId);

    List<TelegramSubscriber> findByActiveTrueOrderByStartedAtDesc();

    boolean existsByChatIdAndActiveTrue(Long chatId);
}
