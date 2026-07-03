package com.pbms.modules.identity.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.identity.dto.UserDTO;
import com.pbms.modules.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDTO.UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers(), "Users fetched successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createUser(@Valid @RequestBody UserDTO.CreateUserRequest request) {
        userService.createUser(request);
        return ResponseEntity.ok(ApiResponse.success("User created successfully", "A password has been sent to the user's email"));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<String>> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully", "User can no longer log in"));
    }

    @PutMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<String>> resetUserPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", "New password has been sent to the user's email"));
    }
}
