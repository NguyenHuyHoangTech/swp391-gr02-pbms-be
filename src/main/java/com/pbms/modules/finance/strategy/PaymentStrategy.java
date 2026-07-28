// Author: Võ Trung Hiếu
package com.pbms.modules.finance.strategy;

public interface PaymentStrategy {
    
    /**
     * Táº¡o mÃ£ QR code hoáº·c URL thanh toÃ¡n
     * @param amount Sá»‘ tiá»n
     * @param orderId MÃ£ Ä‘Æ¡n hÃ ng
     * @return URL thanh toÃ¡n hoáº·c QR Data
     */
    String generatePaymentUrl(double amount, String orderId);
    
    /**
     * XÃ¡c thá»±c Webhook tráº£ vá» tá»« Gateway
     * @param payload Chuá»—i JSON hoáº·c query params
     * @param signature Chá»¯ kÃ½ xÃ¡c thá»±c
     * @return Há»£p lá»‡ hay khÃ´ng
     */
    boolean verifyWebhookSignature(String payload, String signature);
    
    /**
     * MÃ£ Gateway há»— trá»£
     */
    String getProviderCode();
}

