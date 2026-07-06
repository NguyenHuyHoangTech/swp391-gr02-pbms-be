package com.pbms.modules.system.service;

import com.pbms.modules.system.domain.SystemConfig;
import com.pbms.modules.system.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SystemConfigService {

    private final SystemConfigRepository repository;

    public SystemConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

    public List<SystemConfig> getAllConfigs() {
        return repository.findAll();
    }

    public SystemConfig getConfigByKey(String key) {
        return repository.findByConfigKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Config not found with key: " + key));
    }

    @Transactional
    public SystemConfig saveOrUpdateConfigValue(String key, String value) {
        SystemConfig config = repository.findByConfigKey(key).orElse(null);
        if (config == null) {
            config = SystemConfig.builder()
                .configKey(key)
                .configValue(value)
                .description("Default system configuration")
                .build();
            return repository.save(config);
        } else {
            config.setConfigValue(value);
            return repository.save(config);
        }
    }

    @Transactional
    public SystemConfig createConfig(SystemConfig config) {
        if (repository.findByConfigKey(config.getConfigKey()).isPresent()) {
            throw new IllegalArgumentException("Config key already exists: " + config.getConfigKey());
        }
        return repository.save(config);
    }

    @Transactional
    public SystemConfig updateConfig(Long id, SystemConfig configDetails) {
        SystemConfig config = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found with id: " + id));

        config.setConfigValue(configDetails.getConfigValue());
        config.setDescription(configDetails.getDescription());
        
        return repository.save(config);
    }

    @Transactional
    public void deleteConfig(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Config not found with id: " + id);
        }
        repository.deleteById(id);
    }

    public void testSmtpConnection(String email, String appPassword) {
        org.springframework.mail.javamail.JavaMailSenderImpl mailSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        mailSender.setUsername(email);
        mailSender.setPassword(appPassword);

        java.util.Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        // Enforce 5000ms timeout
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");

        try {
            mailSender.testConnection();
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalArgumentException("SMTP Connection failed: " + e.getMessage());
        }
    }
    public void testPaypalConnection(String clientId, String secret) {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBasicAuth(clientId, secret);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

        org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>("grant_type=client_credentials", headers);

        try {
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api-m.sandbox.paypal.com/v1/oauth2/token", request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalArgumentException("PayPal Connection failed: Invalid credentials");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("PayPal Connection failed: " + e.getMessage());
        }
    }
    public void testPayosConnection(String clientId, String apiKey) {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("x-client-id", clientId);
        headers.set("x-api-key", apiKey);

        org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(headers);

        try {
            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    "https://api-merchant.payos.vn/v2/payment-requests/123456", 
                    org.springframework.http.HttpMethod.GET, 
                    request, String.class);
                    
            String body = response.getBody();
            if (body != null) {
                if (body.contains("\"code\":\"214\"")) {
                    throw new IllegalArgumentException("PayOS Connection failed: Invalid Client ID (Cổng thanh toán không tồn tại)");
                }
                if (body.contains("\"code\":\"401\"")) {
                    throw new IllegalArgumentException("PayOS Connection failed: Invalid API Key or Unauthorized");
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == org.springframework.http.HttpStatus.UNAUTHORIZED) {
                throw new IllegalArgumentException("PayOS Connection failed: Invalid credentials");
            }
            // Other errors like 404 mean credentials are fine but record not found
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("PayOS Connection failed: " + e.getMessage());
        }
    }

    public List<java.util.Map<String, String>> testGeminiConnectionAndGetModels(String apiKey) {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        try {
            org.springframework.http.ResponseEntity<String> response = restTemplate.getForEntity(
                    "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                String body = response.getBody();
                List<java.util.Map<String, String>> testedModels = new java.util.ArrayList<>();
                
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(body);
                    com.fasterxml.jackson.databind.JsonNode modelsNode = root.get("models");
                    if (modelsNode != null && modelsNode.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode node : modelsNode) {
                            String name = node.get("name") != null ? node.get("name").asText() : "";
                            
                            // Check if it's a Gemini model and supports generateContent
                            boolean isGemini = name.contains("gemini");
                            boolean supportsGenerate = false;
                            com.fasterxml.jackson.databind.JsonNode methods = node.get("supportedGenerationMethods");
                            if (methods != null && methods.isArray()) {
                                for (com.fasterxml.jackson.databind.JsonNode method : methods) {
                                    if ("generateContent".equals(method.asText())) {
                                        supportsGenerate = true;
                                        break;
                                    }
                                }
                            }
                            
                            if (isGemini && supportsGenerate) {
                                // TEST THE MODEL
                                String url = "https://generativelanguage.googleapis.com/v1beta/" + name + ":generateContent?key=" + apiKey;
                                
                                com.fasterxml.jackson.databind.node.ObjectNode payload = mapper.createObjectNode();
                                com.fasterxml.jackson.databind.node.ArrayNode contents = payload.putArray("contents");
                                com.fasterxml.jackson.databind.node.ObjectNode content = contents.addObject();
                                content.put("role", "user");
                                com.fasterxml.jackson.databind.node.ArrayNode parts = content.putArray("parts");
                                com.fasterxml.jackson.databind.node.ObjectNode part = parts.addObject();
                                part.put("text", "Hi");
                                
                                com.fasterxml.jackson.databind.node.ObjectNode generationConfig = payload.putObject("generationConfig");
                                generationConfig.put("maxOutputTokens", 5);
                                
                                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(mapper.writeValueAsString(payload), headers);
                                
                                // Sleep 500ms to avoid burst rate-limits on Free Tier
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                               
                                
                                try {
                                    org.springframework.http.ResponseEntity<String> genResponse = restTemplate.postForEntity(url, entity, String.class);
                                    if (genResponse.getStatusCode().is2xxSuccessful()) {
                                        com.fasterxml.jackson.databind.JsonNode genRoot = mapper.readTree(genResponse.getBody());
                                        String replyText = genRoot.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
                                        
                                        java.util.Map<String, String> resMap = new java.util.HashMap<>();
                                        resMap.put("model", name);
                                        resMap.put("response", replyText.trim());
                                        testedModels.add(resMap);
                                    }
                                } catch (Exception ex) {
                                    // Skip models that fail to generate
                                    java.util.Map<String, String> resMap = new java.util.HashMap<>();
                                    resMap.put("model", name);
                                    resMap.put("response", "Error: " + ex.getMessage());
                                    testedModels.add(resMap);
                                }
                            }
                        }
                    }
                } catch (Exception parseEx) {
                    throw new IllegalArgumentException("Failed to parse Gemini API response");
                }
                
                if (testedModels.isEmpty()) {
                    throw new IllegalArgumentException("API Key is valid but no compatible Gemini models found.");
                }
                return testedModels;
            } else {
                throw new IllegalArgumentException("Gemini Connection failed: " + response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new IllegalArgumentException("Gemini Connection failed: Invalid API Key");
        } catch (Exception e) {
            throw new IllegalArgumentException("Gemini Connection failed: " + e.getMessage());
        }
    }
}

