package com.pbms.modules.finance.strategy;

import org.springframework.stereotype.Component;

@Component
public class PayOsStrategy implements PaymentStrategy {

    @Override
    public String generatePaymentUrl(double amount, String orderId) {
        // TODO: Call PayOS API to get checkout URL
        return "https://pay.payos.vn/web/...";
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        // TODO: Check PayOS checksum
        return true;
    }

    @Override
    public String getProviderCode() {
        return "PAYOS";
    }
}

