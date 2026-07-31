/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-25
 * @Description: Builds the zone routing advisory prompt from live occupancy data and the
 *               current rule configuration, sends it to the Gemini API, and returns the
 *               suggested configuration as a JSON string.
 * @Dependencies:
 * - AiRoutingRequest (com.pbms.modules.ai.dto.AiRoutingRequest)
 * - SystemConfigService (com.pbms.modules.system.service.SystemConfigService)
 */
package com.pbms.modules.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pbms.modules.ai.dto.AiRoutingRequest;
import com.pbms.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class GeminiService {

    private final SystemConfigService systemConfigService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @Function: getRoutingAdvice
     * @Description: Gọi mô hình ngôn ngữ để phân tích dữ liệu mật độ và cấu hình điều
     *               phối hiện tại, trả về chuỗi JSON chứa cấu hình được đề xuất.
     * @Logic_Steps:
     * 1. Đọc GEMINI_API_KEY từ cấu hình hệ thống. Khoá KHÔNG nằm trong mã nguồn hay
     *    file properties — thiếu khoá thì dừng ngay với thông báo rõ ràng.
     * 2. Đọc GEMINI_MODEL, nếu chưa cấu hình thì dùng mô hình mặc định.
     * 3. Dựng prompt từ dữ liệu request (buildPrompt).
     * 4. Đóng gói payload theo định dạng Gemini, ép kiểu phản hồi là JSON thuần để
     *    Frontend parse được ngay, không phải bóc khối markdown.
     * 5. Gửi request; nếu thành công thì bóc phần text trong candidates[0] trả về.
     * 6. Mọi lỗi giao tiếp đều được bọc lại kèm nguyên nhân gốc để dễ truy vết.
     */
    public String getRoutingAdvice(AiRoutingRequest request) {
        String apiKey;
        try {
            apiKey = systemConfigService.getConfigByKey("GEMINI_API_KEY").getConfigValue();
        } catch (Exception e) {
            throw new IllegalArgumentException("GEMINI_API_KEY is not configured in System Config.");
        }

        String model;
        try {
            model = systemConfigService.getConfigByKey("GEMINI_MODEL").getConfigValue();
        } catch (Exception e) {
            model = "models/gemini-1.5-flash"; // Default
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/" + model + ":generateContent?key=" + apiKey;

        try {
            String promptText = buildPrompt(request);

            ObjectNode payload = mapper.createObjectNode();

            ArrayNode contents = payload.putArray("contents");
            ObjectNode content = contents.addObject();
            content.put("role", "user");
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", promptText);

            ObjectNode generationConfig = payload.putObject("generationConfig");
            generationConfig.put("response_mime_type", "application/json");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.getBody());
                com.fasterxml.jackson.databind.JsonNode candidates = root.get("candidates");
                if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                    com.fasterxml.jackson.databind.JsonNode partsNode = candidates.get(0).get("content").get("parts");
                    if (partsNode != null && partsNode.isArray() && partsNode.size() > 0) {
                        return partsNode.get(0).get("text").asText();
                    }
                }
            }
            throw new IllegalArgumentException("Failed to get a valid response from Gemini");
        } catch (Exception e) {
            throw new RuntimeException("Error communicating with Gemini API: " + e.getMessage(), e);
        }
    }

    /**
     * @Function: buildPrompt
     * @Description: Ghép toàn bộ ngữ cảnh thành 1 prompt tiếng Anh cho mô hình.
     * @Logic_Steps:
     * 1. Nêu vai trò của mô hình và loại xe + khoảng thời gian đang phân tích.
     * 2. Chèn dữ liệu biểu đồ mật độ theo giờ dưới dạng JSON.
     * 3. Nêu rõ tính năng điều phối đang BẬT hay TẮT — hai trạng thái này cần hai
     *    hướng tư vấn khác hẳn nhau, nên phải nói tường minh thay vì để mô hình đoán.
     * 4. Chèn cấu hình luật hiện tại, kèm ghi chú rằng khung giờ cuối cùng thường là
     *    khung mặc định (không có giờ bắt đầu/kết thúc) để mô hình không hiểu nhầm.
     * 5. Chèn thêm ngữ cảnh do quản lý tự nhập, nếu có.
     * 6. Ràng buộc định dạng đầu ra là JSON thuần theo đúng khuôn mẫu cho sẵn.
     */
    private String buildPrompt(AiRoutingRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert in smart parking lot planning (AI Zone Routing Advisor).\n");
        prompt.append("Below is the hourly occupancy data for vehicle type '").append(request.getVehicleType())
                .append("' during the time period: ").append(request.getDateRange()).append("\n\n");

        prompt.append("Chart data (JSON):\n");
        try {
            prompt.append(mapper.writeValueAsString(request.getChartData())).append("\n\n");
        } catch (Exception e) {
            prompt.append("[]\n\n");
        }

        if (!request.isRoutingEnabled()) {
            prompt.append(
                    "IMPORTANT STATUS: The Automatic Routing feature is currently DISABLED by the manager. Therefore, vehicles are entering freely based on physical convenience (first come first serve). Below is the inactive configuration for reference. Please analyze the data to see what issues this 'free-flow' approach is causing, suggest an optimal configuration, and encourage management to turn the feature back ON.\n\n");
        } else {
            prompt.append(
                    "IMPORTANT STATUS: The Automatic Routing feature is currently ENABLED. Below is the current configuration. Please analyze it, point out any flaws (e.g. thresholds too high during peak hours), and suggest an optimized configuration.\n\n");
        }

        prompt.append("Current Configuration (JSON):\n");
        try {
            prompt.append(mapper.writeValueAsString(request.getCurrentRules())).append("\n\n");
        } catch (Exception e) {
            prompt.append("[]\n\n");
        }

        prompt.append(
                "NOTE: The last time frame in the configuration is usually the 'Default' timeframe (isDefault=true). It has no specific start/end time because it acts as a fallback for all hours not covered by the specific timeframes, ensuring the system runs smoothly 24/7.\n\n");

        if (request.getExtraContext() != null && !request.getExtraContext().trim().isEmpty()) {
            prompt.append("Additional context from Management (VERY IMPORTANT):\n");
            prompt.append(request.getExtraContext()).append("\n\n");
        }

        prompt.append("YOUR TASK:\n");
        prompt.append(
                "Analyze the data above and suggest how to coordinate the parking zones for different time frames of the day.\n");
        prompt.append("Return ONLY a JSON string with the following exact format (no markdown blocks ```json):\n");
        prompt.append("{\n");
        prompt.append(
                "  \"reasoning\": \"A short explanation of why this configuration is recommended (from a data analysis and context perspective).\",\n");
        prompt.append("  \"timeFrames\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"Morning - Peak (example)\",\n");
        prompt.append("      \"startTime\": \"07:00\",\n");
        prompt.append("      \"endTime\": \"11:00\",\n");
        prompt.append("      \"rules\": [\n");
        prompt.append("        { \"zoneName\": \"Zone A\", \"threshold\": 85, \"priority\": 1 },\n");
        prompt.append("        { \"zoneName\": \"Zone B\", \"threshold\": 90, \"priority\": 2 }\n");
        prompt.append("      ]\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");

        return prompt.toString();
    }
}
