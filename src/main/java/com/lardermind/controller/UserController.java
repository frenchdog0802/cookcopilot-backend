package com.lardermind.controller;

import com.lardermind.common.ApiResponse;
import com.lardermind.entity.User;
import com.lardermind.dto.*;
import jakarta.validation.Valid;
import com.lardermind.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .build();
    }

    @GetMapping
    public ApiResponse<ListUsersResponse> list() {
        List<UserDto> dtos = userService.listAll().stream().map(this::toDto).toList();
        return ApiResponse.success(new ListUsersResponse(dtos));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReadUserResponse> read(@PathVariable UUID id) {
        User user = userService.findById(id);
        return ApiResponse.success(new ReadUserResponse(toDto(user)));
    }

    @PutMapping("/{id}")
    public ApiResponse<UpdateUserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        User updates = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .build();
        User user = userService.update(id, updates);
        return ApiResponse.success(new UpdateUserResponse(toDto(user)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteUserResponse> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ApiResponse.success(new DeleteUserResponse("User deleted"));
    }
}
