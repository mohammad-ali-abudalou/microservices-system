package org.example.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.event.OrderPlacedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    @KafkaListener(topics = "notificationTopic", groupId = "notification-group")
    public void handleNotification(OrderPlacedEvent orderPlacedEvent) {
        log.info("Received new event from Kafka: Order Number {}", orderPlacedEvent.getOrderNumber());

        // تفويض المهمة لمنطق الإرسال
        sendEmailNotification(orderPlacedEvent.getOrderNumber());
    }

    private void sendEmailNotification(String orderNumber) {
        // هنا يتم وضع منطق إرسال الإيميل الحقيقي
        log.info("Successfully processed notification for order: {}", orderNumber);
        log.info("Email sent to customer for order reference: {}", orderNumber);
    }
}