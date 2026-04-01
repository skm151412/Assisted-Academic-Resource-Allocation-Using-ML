package com.college.ara.controller;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.college.ara.model.User;
import com.college.ara.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            return unauthorized("Username and password are required");
        }

        String username = request.getUsername().trim();
        String password = request.getPassword().trim();
        if (username.isEmpty() || password.isEmpty()) {
            return unauthorized("Username and password are required");
        }

        return userService.findByUsername(username)
                .filter(User::isActive)
                .filter(user -> user.getPassword() != null && user.getPassword().trim().equals(password))
                .map(user -> ResponseEntity.ok(toResponse(user)))
                .orElseGet(() -> unauthorized("Invalid username or password"));
    }

    private AuthResponse toResponse(User user) {
        AuthResponse response = new AuthResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole().name());
        response.setFullName(user.getFullName());
        response.setDepartment(user.getDepartment());
        response.setSubjectsHandled(user.getSubjectsHandled());
        response.setToken(buildToken(user));
        response.setMessage("Login successful");
        return response;
    }

    private ResponseEntity<AuthResponse> unauthorized(String message) {
        AuthResponse response = new AuthResponse();
        response.setMessage(message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    private String buildToken(User user) {
        String rawToken = user.getUsername() + ":" + user.getRole().name();
        return Base64.getEncoder().encodeToString(rawToken.getBytes(StandardCharsets.UTF_8));
    }

    public static class AuthRequest {

        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class AuthResponse {

        private Long id;
        private String username;
        private String role;
        private String fullName;
        private String department;
        private String subjectsHandled;
        private String token;
        private String message;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public String getSubjectsHandled() {
            return subjectsHandled;
        }

        public void setSubjectsHandled(String subjectsHandled) {
            this.subjectsHandled = subjectsHandled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
