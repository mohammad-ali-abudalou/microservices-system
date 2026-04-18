package org.example.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor // ضروري جداً للاستقبال
public class OrderPlacedEvent {
    private String orderNumber;
}