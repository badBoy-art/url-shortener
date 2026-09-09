package com.urlshortener.controller;

import com.urlshortener.common.ApiResponse;
import com.urlshortener.dto.CreateUserRequest;
import com.urlshortener.dto.ResetPasswordRequest;
import com.urlshortener.dto.UserResponse;
import com.urlshortener.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "管理员用户管理", description = "管理员账号列表、新增、重置密码（需 JWT 或 X-API-Key）")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "管理员列表（分页，不含密码）")
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        List<UserResponse> users = adminUserService.listUsers(page, size);
        return ApiResponse.ok(Map.of(
                "page", page,
                "size", size,
                "total", adminUserService.countUsers(),
                "list", users));
    }

    @Operation(summary = "新增管理员", description = "账号 5-64 位，密码至少 8 位")
    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(adminUserService.createUser(request));
    }

    @Operation(summary = "重置管理员密码")
    @PutMapping("/{account}/password")
    public ApiResponse<Void> resetPassword(@PathVariable String account,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(account, request);
        return ApiResponse.ok();
    }
}
