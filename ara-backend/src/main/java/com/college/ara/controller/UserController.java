package com.college.ara.controller;

import com.college.ara.model.User;
import com.college.ara.service.UserService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public User create(@RequestBody User user) {
        return userService.createUser(user);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(userService.getUser(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(ex.getMessage()));
        }
    }

    @GetMapping
    public List<User> list() {
        return userService.listUsers();
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<?> updateProfile(@PathVariable("id") Long id, @RequestBody UpdateProfileRequest request) {
        try {
            if (request == null) {
                return ResponseEntity.badRequest().body(error("Profile details are required"));
            }

            User updated = userService.updateProfile(
                    id,
                    request.getFullName(),
                    request.getDepartment(),
                    request.getSubjectsHandled(),
                    request.getPassword());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to update profile"));
        }
    }

    private Map<String, String> error(String message) {
        return Map.of("message", message);
    }

    public static class UpdateProfileRequest {
        private String fullName;
        private String department;
        private String subjectsHandled;
        private String password;

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

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
