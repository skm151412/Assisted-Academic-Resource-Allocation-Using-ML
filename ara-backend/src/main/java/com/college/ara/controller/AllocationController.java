package com.college.ara.controller;

import com.college.ara.model.Allocation;
import com.college.ara.service.AllocationService;
import com.college.ara.repository.ResourceRepository;
import com.college.ara.model.Resource;
import com.college.ara.model.User;
import com.college.ara.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

@RestController
@RequestMapping("/api/allocations")
public class AllocationController {
    private static final Set<String> ALLOWED_ROOM_CODES = Set.of(
            "H1-01", "H1-02", "H1-03", "H1-04",
            "H1-17", "H1-18", "H1-19", "H1-22", "H1-23", "H1-25", "H1-26");

    private final AllocationService allocationService;
    private final ResourceRepository resourceRepository;
    private final UserService userService;

    public AllocationController(AllocationService allocationService, ResourceRepository resourceRepository, UserService userService) {
        this.allocationService = allocationService;
        this.resourceRepository = resourceRepository;
        this.userService = userService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody(required = false) Map<String, Object> request) {
        try {
            return ResponseEntity.ok(allocationService.generateAllocations(request));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to generate allocations"));
        }
    }

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            return ResponseEntity.ok(allocationService.getAllAllocations());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to load allocations"));
        }
    }

    @GetMapping("/faculty/{userId}")
    public ResponseEntity<?> listForFaculty(@PathVariable("userId") Long userId) {
        try {
            User user = userService.getUser(userId);
            return ResponseEntity.ok(allocationService.getAllocationsForFaculty(user.getFullName()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to load faculty timetable"));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        try {
            List<Allocation> allocations = allocationService.getAllAllocations();
            List<Resource> managedResources = resourceRepository.findAll().stream()
                    .filter(Resource::isActive)
                    .filter(resource -> ALLOWED_ROOM_CODES.contains(resource.getResourceCode()))
                    .toList();

            long totalAllocations = allocations.size();
            long usedRooms = allocations.stream()
                    .filter(allocation -> allocation.getRoom() != null && allocation.getRoom().getId() != null)
                    .map(allocation -> allocation.getRoom().getId())
                    .distinct()
                    .count();
            int totalRooms = managedResources.size();

            int freeRooms = Math.max(totalRooms - (int) usedRooms, 0);
            double usagePercent = totalRooms == 0 ? 0 : ((double) usedRooms / totalRooms) * 100.0;

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalAllocations", totalAllocations);
            stats.put("roomUsagePercent", String.format("%.1f", usagePercent));
            stats.put("freeRooms", freeRooms);
            stats.put("managedRooms", totalRooms);

            return ResponseEntity.ok(stats);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to load allocation stats"));
        }
    }

    private Map<String, String> error(String message) {
        return Map.of("message", message);
    }
}
