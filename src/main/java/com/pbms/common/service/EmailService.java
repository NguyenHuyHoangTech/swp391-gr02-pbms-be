package com.pbms.common.service;

import com.pbms.modules.system.domain.SystemConfig;
import com.pbms.modules.system.service.SystemConfigService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Service dùng để gửi email trong hệ thống.
 * EmailService lấy thông tin SMTP từ SystemConfigService trong module system,
 * sau đó dùng JavaMailSenderImpl để gửi email HTML cho người dùng.
 * Method gửi email có @Async nên sẽ chạy background dựa trên cấu hình AsyncConfig trong common.config.
 *
 * Pseudo code:
 * 1. Lấy email SMTP và app password từ cấu hình hệ thống trong database.
 * 2. Tạo mail sender dùng SMTP Gmail.
 * 3. Cấu hình giao thức gửi mail.
 * 4. Tạo email HTML.
 * 5. Gửi email ở background để không làm chậm request chính.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final SystemConfigService systemConfigService;

    /**
     * Tạo JavaMailSenderImpl để gửi email qua SMTP.
     * Method này đọc thông tin SMTP_EMAIL và SMTP_APP_PASSWORD từ SystemConfigService.
     *
     * Pseudo code:
     * 1. Tạo JavaMailSenderImpl.
     * 2. Gán SMTP host là smtp.gmail.com.
     * 3. Gán SMTP port là 587.
     * 4. Lấy SMTP_EMAIL từ database.
     * 5. Lấy SMTP_APP_PASSWORD từ database.
     * 6. Gán email và app password vào mail sender.
     * 7. Nếu thiếu cấu hình SMTP thì ghi cảnh báo vào log.
     * 8. Cấu hình SMTP protocol, authentication và STARTTLS.
     * 9. Trả về mail sender để dùng khi gửi email.
     */
    private JavaMailSenderImpl getMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);

        try {
            SystemConfig emailConfig = systemConfigService.getConfigByKey("SMTP_EMAIL");
            SystemConfig passConfig = systemConfigService.getConfigByKey("SMTP_APP_PASSWORD");
            
            mailSender.setUsername(emailConfig.getConfigValue());
            mailSender.setPassword(passConfig.getConfigValue());
        } catch (Exception e) {
            log.warn("SMTP config missing from database. Falling back to default or failing: {}", e.getMessage());
        }

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        return mailSender;
    }

    /**
     * Gửi email HTML đến người nhận.
     * Method này chạy bất đồng bộ bằng @Async, nên request chính không cần chờ gửi email xong.
     *
     * Pseudo code:
     * 1. Nhận email người nhận, tiêu đề và nội dung HTML.
     * 2. Tạo mail sender từ cấu hình SMTP.
     * 3. Tạo MimeMessage.
     * 4. Gán người nhận.
     * 5. Gán tiêu đề email.
     * 6. Gán nội dung email dạng HTML.
     * 7. Gửi email.
     * 8. Nếu gửi thành công thì ghi log thành công.
     * 9. Nếu gửi thất bại thì ghi log lỗi.
     */
    @Async
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            JavaMailSenderImpl mailSender = getMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}", to, e);
        }
    }
}