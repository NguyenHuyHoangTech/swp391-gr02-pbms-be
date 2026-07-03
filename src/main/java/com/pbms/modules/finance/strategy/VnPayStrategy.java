package com.pbms.modules.finance.strategy;

import org.springframework.stereotype.Component;

@Component
public class VnPayStrategy implements PaymentStrategy {

    @Override
    public String generatePaymentUrl(double amount, String orderId) {
        // TODO: Implement VNPay URL generation logic (VNPAY_TmnCode, HashSecret, etc.)
        return "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...";
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        // TODO: Implement VNPay HMAC SHA512 check
        return true;
    }

    @Override
    public String getProviderCode() {
        return "VNPAY";
    }
}

