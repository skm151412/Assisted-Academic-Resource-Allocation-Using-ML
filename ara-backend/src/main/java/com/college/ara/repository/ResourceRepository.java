package com.college.ara.repository;

import com.college.ara.model.Resource;
import com.college.ara.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    Optional<Resource> findByResourceCode(String resourceCode);
    List<Resource> findByResourceTypeAndActiveTrue(ResourceType resourceType);
}
