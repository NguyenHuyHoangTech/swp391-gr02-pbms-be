package com.pbms.modules.finance.strategy;

import com.pbms.modules.system.domain.SystemConfig;
import com.pbms.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayPalStrategy implements PaymentStrategy {

    private final SystemConfigService systemConfigService;
    private final RestTemplate restTemplate = new RestTemplate();
    
    private static final String PAYPAL_SANDBOX_API = "https://api-m.sandbox.paypal.com";

    private String getAccessToken() {
        try {
            SystemConfig clientIdConfig = systemConfigService.getConfigByKey("PAYPAL_CLIENT_ID");
            SystemConfig secretConfig = systemConfigService.getConfigByKey("PAYPAL_SECRET");
            
            String clientId = clientIdConfig.getConfigValue();
            String secret = secretConfig.getConfigValue();
            
            String auth = clientId + ":" + secret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "Basic " + encodedAuth);
            
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(PAYPAL_SANDBOX_API + "/v1/oauth2/token", request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            log.error("Error getting PayPal access token: {}", e.getMessage());
            throw new RuntimeException("An error occurred");
        }
        throw new RuntimeException("PayPal access Token is not allowed");
    }

    @Override
    public String generatePaymentUrl(double amount, String orderId) {
        String accessToken = getAccessToken();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        
        // USD amount conversion roughly (e.g. 1 USD = 25000 VND)
        double usdAmount = Math.round((amount / 25000.0) * 100.0) / 100.0;
        if (usdAmount <= 0) usdAmount = 1.00; // minimum amount

        Map<String, Object> amountMap = Map.of(
                "currency_code", "USD",
                "value", String.format("%.2f", usdAmount).replace(",", ".")
        );
        
        Map<String, Object> purchaseUnit = Map.of(
                "reference_id", orderId,
                "amount", amountMap
        );
        
        Map<String, Object> requestBody = Map.of(
                "intent", "CAPTURE",
                "purchase_units", Collections.singletonList(purchaseUnit),
                "application_context", Map.of(
                        "return_url", "https://www.google.com/search?q=Thanh+toan+PayPal+Thanh+Cong",
                        "cancel_url", "https://www.google.com/search?q=Thanh+toan+PayPal+That+Bai",
                        "user_action", "PAY_NOW"
                )
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(PAYPAL_SANDBOX_API + "/v2/checkout/orders", request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("links")) {
                    List<Map<String, String>> links = (List<Map<String, String>>) body.get("links");
                    for (Map<String, String> link : links) {
                        if ("approve".equalsIgnoreCase(link.get("rel"))) {
                            return link.get("href"); // The approval URL
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error creating PayPal order: {}", e.getMessage());
            throw new RuntimeException("An error occurred");
        }
        
        throw new RuntimeException("Do not check the PayPal approval link");
    }

    public boolean captureOrder(String paypalOrderId) {
        String accessToken = getAccessToken();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        
        HttpEntity<String> request = new HttpEntity<>("", headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    PAYPAL_SANDBOX_API + "/v2/checkout/orders/" + paypalOrderId + "/capture", 
                    request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                Map<String, Object> body = response.getBody();
                if (body != null && "COMPLETED".equalsIgnoreCase((String) body.get("status"))) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Error capturing PayPal order: {}", e.getMessage());
            // If already captured, it might return 422 Unprocessable Entity
            if (e.getMessage().contains("ORDER_ALREADY_CAPTURED")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        return true;
    }

    @Override
    public String getProviderCode() {
        return "PAYPAL";
    }
}

