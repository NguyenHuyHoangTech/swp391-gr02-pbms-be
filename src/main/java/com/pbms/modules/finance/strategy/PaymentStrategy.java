package com.pbms.modules.finance.strategy;

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
     * MÃ£ Gateway hỗ trợ (VD: "VNPAY", "PAYOS", "PAYPAL")
     */
    String getProviderCode();

    /**
     * [PBMS-76] Xác nhận (Capture) đơn hàng.
     * Sử dụng 'default' để không làm lỗi các Strategy không cần capture (như VNPay, PayOS).
     * Riêng PayPalStrategy sẽ @Override lại hàm này để chứa logic gọi API thật.
     * * @param token Mã xác thực từ cổng thanh toán
     * @return true nếu thành công
     */
    default boolean captureOrder(String token) {
        // Mặc định trả về false hoặc throw Exception nếu cổng thanh toán không hỗ trợ thao tác này
        return false;
    }
}

