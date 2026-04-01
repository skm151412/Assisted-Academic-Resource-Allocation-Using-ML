package com.college.ara.controller;

import com.college.ara.model.Resource;
import com.college.ara.model.ResourceType;
import com.college.ara.service.ResourceService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateResourceRequest request) {
        try {
            if (request == null || request.getResourceCode() == null || request.getResourceName() == null) {
                return ResponseEntity.badRequest().body(error("Resource code and name are required"));
            }
            Resource resource = new Resource();
            resource.setResourceCode(request.getResourceCode().trim());
            resource.setResourceName(request.getResourceName().trim());
            resource.setResourceType(request.getResourceType());
            resource.setCapacity(request.getCapacity());
            resource.setLocation(request.getLocation() == null ? "" : request.getLocation().trim());
            resource.setActive(request.isActive());
            return ResponseEntity.status(HttpStatus.CREATED).body(resourceService.createResource(resource));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to create resource"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(resourceService.getResource(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to load resource"));
        }
    }

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            List<Resource> resources = resourceService.listResources();
            return ResponseEntity.ok(resources);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to load resources"));
        }
    }

    @GetMapping("/managed")
    public ResponseEntity<?> listManaged() {
        try {
            return ResponseEntity.ok(resourceService.listManagedResources());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to load managed resources"));
        }
    }

    @GetMapping("/free")
    public ResponseEntity<?> listFree(
            @RequestParam("startTime") String startTime,
            @RequestParam("endTime") String endTime,
            @RequestParam(value = "resourceType", required = false) ResourceType resourceType) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            return ResponseEntity.ok(resourceService.findFreeResources(start, end, resourceType));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to load free rooms"));
        }
    }

    private Map<String, String> error(String message) {
        return Map.of("message", message);
    }

    public static class CreateResourceRequest {
        private String resourceCode;
        private String resourceName;
        private ResourceType resourceType;
        private int capacity;
        private String location;
        private boolean active = true;

        public String getResourceCode() {
            return resourceCode;
        }

        public void setResourceCode(String resourceCode) {
            this.resourceCode = resourceCode;
        }

        public String getResourceName() {
            return resourceName;
        }

        public void setResourceName(String resourceName) {
            this.resourceName = resourceName;
        }

        public ResourceType getResourceType() {
            return resourceType;
        }

        public void setResourceType(ResourceType resourceType) {
            this.resourceType = resourceType;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
