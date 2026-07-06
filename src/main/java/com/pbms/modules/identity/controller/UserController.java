package com.pbms.modules.identity.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.identity.dto.UserDTO;
import com.pbms.modules.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.pbms.common.annotation.LogAudit;

@RestController
@RequestMapping("/api/v1/identity/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserDTO.UserResponse>>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        return ResponseEntity.ok(ApiResponse.success(userService.searchUsers(keyword, role, status, pageable), "Users fetched successfully"));
    }

    @PostMapping
    @LogAudit(action = "CREATE", resource = "User", description = "Create new user")
    public ResponseEntity<ApiResponse<String>> createUser(@Valid @RequestBody UserDTO.CreateUserRequest request) {
        userService.createUser(request);
        return ResponseEntity.ok(ApiResponse.success("User created successfully", "A password has been sent to the user's email"));
    }

    @PutMapping("/{id}")
    @LogAudit(action = "UPDATE", resource = "User", description = "Update user details")
    public ResponseEntity<ApiResponse<String>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserDTO.UpdateUserRequest request,
            @AuthenticationPrincipal String currentUserEmail) {
        userService.updateUser(id, request, currentUserEmail);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", "User info updated"));
    }

    @PutMapping("/{id}/status")
    @LogAudit(action = "UPDATE", resource = "User", description = "Update user status")
    public ResponseEntity<ApiResponse<String>> changeUserStatus(
            @PathVariable Long id,
            @RequestParam boolean activate,
            @AuthenticationPrincipal String currentUserEmail) {
        userService.changeUserStatus(id, activate, currentUserEmail);
        String action = activate ? "activated" : "deactivated";
        return ResponseEntity.ok(ApiResponse.success("User " + action + " successfully", "User status updated"));
    }

    @PutMapping("/{id}/reset-password")
    @LogAudit(action = "UPDATE", resource = "User", description = "Reset user password")
    public ResponseEntity<ApiResponse<String>> resetUserPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", "New password has been sent to the user's email"));
    }
}
