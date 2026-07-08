package com.pbms.modules.finance.strategy;

/**
 * PaymentStrategy áp dụng mẫu thiết kế Strategy Pattern.
 * Interface này định nghĩa bộ khung chung cho tất cả các cổng thanh toán.
 * Các cổng (như PayOS, PayPal) sẽ phải tự implement các hàm này theo cách riêng của API bên họ.
 */
public interface PaymentStrategy {
    
    /**
     * Tạo mã QR code hoặc URL thanh toán
     * @param amount Số tiền
     * @param orderId Mã đơn hàng
     * @return URL thanh toán hoặc QR Data
     */
    String generatePaymentUrl(double amount, String orderId);
    
    /**
     * Xác thực Webhook trả về từ Gateway
     * @param payload Chuỗi JSON hoặc query params
     * @param signature Chữ ký xác thực
     * @return Hợp lệ hay không
     */
    boolean verifyWebhookSignature(String payload, String signature);
    
    /**
     * Mã Gateway hỗ trợ
     */
    String getProviderCode();
}
