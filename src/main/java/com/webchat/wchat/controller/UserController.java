package com.webchat.wchat.controller;

import com.webchat.wchat.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/users/available")
    public List<String> getAvailableUsers(Principal principal) {
        // Fetch all usernames except the current user
        return userService.findAllUsernames().stream()
                .filter(username -> !username.equals(principal.getName()))
                .collect(Collectors.toList());
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            Principal principal,
            HttpServletRequest request
    ) {
        try {
            userService.changePassword(principal.getName(), currentPassword, newPassword);
            request.getSession().invalidate();
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}