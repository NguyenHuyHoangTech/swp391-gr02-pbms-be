package com.pbms.modules.finance.strategy;

import com.pbms.modules.system.domain.SystemConfig;
import com.pbms.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.type.PaymentLinkData;

import java.util.HashMap;
import java.util.Map;

/**
 * PayOsStrategy là lớp xử lý thanh toán qua cổng PayOS.
 * Nó chịu trách nhiệm tạo cấu hình kết nối, ký HMAC bằng mã checksum
 * và gửi HTTP POST request đến API của PayOS để lấy URL thanh toán QR.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayOsStrategy implements PaymentStrategy {

    private final SystemConfigService systemConfigService;

    private PayOS getPayOS() {
        SystemConfig clientIdConfig = systemConfigService.getConfigByKey("PAYOS_CLIENT_ID");
        SystemConfig apiKeyConfig = systemConfigService.getConfigByKey("PAYOS_API_KEY");
        SystemConfig checksumKeyConfig = systemConfigService.getConfigByKey("PAYOS_CHECKSUM_KEY");

        return new PayOS(
            clientIdConfig.getConfigValue().trim(), 
            apiKeyConfig.getConfigValue().trim(), 
            checksumKeyConfig.getConfigValue().trim()
        );
    }

    @Override
    public String generatePaymentUrl(double amount, String orderId) {
        return generatePayOsLink(amount, orderId).get("checkoutUrl");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, String> generatePayOsLink(double amount, String orderId) {
        // PayOS quy định số tiền thanh toán tối thiểu là 2000 VND
        if (amount < 2000) {
            throw new IllegalArgumentException("PayOS requires a minimum amount of 2,000 VND (Current: " + amount + ")");
        }

        // Luồng tạo link:
        // 1. Đọc Client ID, API Key, Checksum Key từ bảng system_configs.
        // 2. Tạo signature data (bao gồm giá tiền, returnUrl, cancelUrl).
        // 3. Dùng Checksum Key để tạo chữ ký HMAC SHA256.
        // 4. Gửi HTTP Request sang PayOS.
        try {
            SystemConfig clientIdConfig = systemConfigService.getConfigByKey("PAYOS_CLIENT_ID");
            SystemConfig apiKeyConfig = systemConfigService.getConfigByKey("PAYOS_API_KEY");
            SystemConfig checksumKeyConfig = systemConfigService.getConfigByKey("PAYOS_CHECKSUM_KEY");

            String clientId = clientIdConfig.getConfigValue().trim();
            String apiKey = apiKeyConfig.getConfigValue().trim();
            String checksumKey = checksumKeyConfig.getConfigValue().trim();

            long orderCode = com.pbms.common.utils.TimeProvider.now().toInstant(java.time.ZoneOffset.UTC).toEpochMilli() % 1000000000L;
            int amountInt = (int) amount;
            String description = "Payment " + orderCode;
            String returnUrl = "http://localhost:5173/success";
            String cancelUrl = "http://localhost:5173/cancel";

            String signatureData = "amount=" + amountInt + "&cancelUrl=" + cancelUrl + "&description=" + description + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl;
            
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(checksumKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(signatureData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String signature = hexString.toString();

            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("x-client-id", clientId);
            headers.set("x-api-key", apiKey);

            java.util.Map<String, Object> bodyPayload = new java.util.HashMap<>();
            bodyPayload.put("orderCode", orderCode);
            bodyPayload.put("amount", amountInt);
            bodyPayload.put("description", description);
            bodyPayload.put("cancelUrl", cancelUrl);
            bodyPayload.put("returnUrl", returnUrl);
            bodyPayload.put("signature", signature);

            org.springframework.http.HttpEntity<java.util.Map<String, Object>> requestEntity = new org.springframework.http.HttpEntity<>(bodyPayload, headers);
            
            org.springframework.http.ResponseEntity<java.util.Map> response = restTemplate.exchange(
                    "https://api-merchant.payos.vn/v2/payment-requests",
                    org.springframework.http.HttpMethod.POST,
                    requestEntity,
                    java.util.Map.class
            );

            java.util.Map<String, Object> resBody = response.getBody();
            if (resBody == null || !"00".equals(resBody.get("code"))) {
                throw new RuntimeException("PayOS Error: " + (resBody != null ? resBody.get("desc") : "Unknown"));
            }

            java.util.Map<String, Object> data = (java.util.Map<String, Object>) resBody.get("data");

            Map<String, String> result = new HashMap<>();
            result.put("checkoutUrl", (String) data.get("checkoutUrl"));
            result.put("qrCode", (String) data.get("qrCode"));
            result.put("orderCode", String.valueOf(orderCode));
            return result;
        } catch (Exception e) {
            log.error("Error creating PayOS order: {}", e.getMessage());
            throw new RuntimeException("Cannot create PayOS payment link: " + e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra trạng thái đơn hàng trực tiếp từ PayOS.
     * Hàm này được dùng trong bước Capture Webhook để xác minh lại giao dịch
     * xem thực sự tiền đã vào tài khoản chưa (tránh fake webhook).
     */
    public boolean captureOrder(String orderCodeStr) {
        try {
            long orderCode = Long.parseLong(orderCodeStr);
            PayOS payOS = getPayOS();
            PaymentLinkData data = payOS.getPaymentLinkInformation(orderCode);
            if (data != null && "PAID".equals(data.getStatus())) {
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error checking PayOS order: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Xác thực chữ ký webhook từ PayOS (hiện đang mock trả về true).
     */
    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        return true;
    }

    /**
     * Trả về định danh của cổng thanh toán này (PAYOS).
     */
    @Override
    public String getProviderCode() {
        return "PAYOS";
    }
}
