package com.college.ara.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.college.ara.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
}
