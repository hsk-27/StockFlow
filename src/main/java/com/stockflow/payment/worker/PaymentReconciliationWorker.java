package com.stockflow.payment.worker;

import com.stockflow.payment.service.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationWorker {

    private final PaymentService paymentService;

    public PaymentReconciliationWorker(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 60000) // Every 60 seconds
    public void runReconciliation() {
        paymentService.reconcileUncertainPayments();
    }
}
