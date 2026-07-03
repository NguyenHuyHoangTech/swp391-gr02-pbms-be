package com.pbms.modules.identity.service;

import com.pbms.modules.identity.dto.UserDTO;
import com.pbms.modules.identity.entity.User;
import com.pbms.modules.identity.repository.UserRepository;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender javaMailSender;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JavaMailSender javaMailSender) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.javaMailSender = javaMailSender;
    }

    private String generateRandomPassword() {
        int length = 8;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public List<UserDTO.UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(user -> {
            UserDTO.UserResponse dto = new UserDTO.UserResponse();
            dto.setId(user.getId());
            dto.setName(user.getName());
            dto.setEmail(user.getEmail());
            dto.setPhone(user.getPhone());
            dto.setRole(user.getRole());
            dto.setIsVerified(user.getIsVerified());
            dto.setIsActive(user.getIsActive());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void createUser(UserDTO.CreateUserRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        String rawPassword = generateRandomPassword();
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(request.getRole())
                .password(passwordEncoder.encode(rawPassword))
                .isVerified(true)
                .isActive(true)
                .build();
        
        userRepository.save(user);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(request.getEmail());
            message.setSubject("Your PBMS Account Created");
            message.setText("Your account has been created.\nEmail: " + request.getEmail() + "\nPassword: " + rawPassword + "\nPlease change it after logging in.");
            javaMailSender.send(message);
        } catch (MailException e) {
            System.err.println("Failed to send welcome email: " + e.getMessage());
        }
    }

    @Transactional
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setIsActive(false);
        userRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        String rawPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(user.getEmail());
            message.setSubject("Your PBMS Password Reset");
            message.setText("Your password has been reset by the Admin.\nNew Password: " + rawPassword + "\nPlease change it after logging in.");
            javaMailSender.send(message);
        } catch (MailException e) {
            System.err.println("Failed to send reset password email: " + e.getMessage());
        }
    }
}
