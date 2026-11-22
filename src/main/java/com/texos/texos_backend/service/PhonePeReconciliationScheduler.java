package com.texos.texos_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PhonePeReconciliationScheduler {
    private final RestaurantPaymentService paymentService;
    private final SubscriptionService subscriptionService;

    @Scheduled(fixedDelayString = "${app.phonepe.reconciliation-interval-ms:30000}")
    public void reconcilePaymentsAndWebhooks() {
        for (Long eventId : paymentService.getPendingWebhookIds()) {
            try {
                paymentService.processWebhookEvent(eventId);
            } catch (RuntimeException ex) {
                log.warn("Unable to process PhonePe webhook event {}", eventId, ex);
            }
        }
        for (String merchantOrderId : paymentService.getUnresolvedMerchantOrderIds()) {
            try {
                paymentService.reconcileAttempt(merchantOrderId);
            } catch (RuntimeException ex) {
                log.warn("Unable to reconcile PhonePe merchant order {}", merchantOrderId, ex);
            }
        }
        for (String merchantOrderId : subscriptionService.getUnresolvedSubscriptionMerchantOrderIds()) {
            try {
                subscriptionService.reconcileSubscriptionPayment(merchantOrderId);
            } catch (RuntimeException ex) {
                log.warn("Unable to reconcile TexOS subscription payment {}", merchantOrderId, ex);
            }
        }
    }
}
