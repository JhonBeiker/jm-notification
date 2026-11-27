package com.jmcode.notification.dispatch.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker programado que drena la cola persistente de notificaciones. Puede desactivarse con
 * {@code notification.queue.worker-enabled=false} (útil en tests deterministas o nodos sólo-API).
 */
@Component
@ConditionalOnProperty(name = "notification.queue.worker-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class NotificationQueueWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationQueueWorker.class);

    private final NotificationQueueService queueService;

    @Scheduled(fixedDelayString = "${notification.queue.poll-interval:5s}")
    public void poll() {
        try {
            int processed = queueService.processPendingBatch();
            if (processed > 0) {
                log.debug("Processed {} queued notification(s)", processed);
            }
        } catch (Exception ex) {
            log.error("Error while polling notification queue", ex);
        }
    }
}
