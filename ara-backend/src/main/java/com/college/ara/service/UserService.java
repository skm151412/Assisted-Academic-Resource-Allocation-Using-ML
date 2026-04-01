package com.college.ara.service;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.ara.model.User;
import com.college.ara.model.UserRole;
import com.college.ara.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        upsertDefaultUser("admin", "admin123", "Administrator", UserRole.ADMIN, "Administration", "Approvals, Resources, Scheduling");
        upsertDefaultUser("faculty", "faculty123", "Dr Suhma Rani", UserRole.FACULTY, "CSE", "MO, Timetable Review");
        upsertDefaultUser("sree.lakshmi", "sree.lakshmi", "Sree Lakshmi", UserRole.FACULTY, "CSE", "FSAD / PFSD");
        upsertDefaultUser("anuradha", "anuradha", "Dr Anuradha", UserRole.FACULTY, "CSE", "FSAD / PFSD");
    }

    @Transactional
    public User createUser(User user) {
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User updateProfile(Long id, String fullName, String department, String subjectsHandled, String password) {
        User user = getUser(id);
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        user.setDepartment(department == null ? "" : department.trim());
        user.setSubjectsHandled(subjectsHandled == null ? "" : subjectsHandled.trim());
        if (password != null && !password.isBlank()) {
            user.setPassword(password);
        }
        return userRepository.save(user);
    }

    private void upsertDefaultUser(String username, String password, String fullName, UserRole role, String department, String subjectsHandled) {
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User();
            user.setUsername(username);
            user.setPassword(password);
            user.setFullName(fullName);
            user.setRole(role);
            user.setDepartment(department);
            user.setSubjectsHandled(subjectsHandled);
            user.setActive(true);
            userRepository.save(user);
        }
    }
}
