package com.college.ara.controller;

import com.college.ara.model.Booking;
import com.college.ara.model.Resource;
import com.college.ara.model.User;
import com.college.ara.service.BookingService;
import com.college.ara.service.ResourceService;
import com.college.ara.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final UserService userService;
    private final ResourceService resourceService;

    public BookingController(BookingService bookingService, UserService userService, ResourceService resourceService) {
        this.bookingService = bookingService;
        this.userService = userService;
        this.resourceService = resourceService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody BookingRequest request) {
        try {
            if (request == null || request.getUserId() == null || request.getResourceId() == null) {
                return ResponseEntity.badRequest().body(error("User and resource are required"));
            }
            User user = userService.getUser(request.getUserId());
            Resource resource = resourceService.getResource(request.getResourceId());
            LocalDateTime startTime = LocalDateTime.parse(request.getStartTime());
            LocalDateTime endTime = LocalDateTime.parse(request.getEndTime());
            Booking booking = bookingService.requestBooking(user, resource, startTime, endTime, request.getPurpose());
            return ResponseEntity.status(HttpStatus.CREATED).body(booking);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to create booking"));
        }
    }

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            return ResponseEntity.ok(bookingService.listBookings());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to load bookings"));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> listForUser(@PathVariable("userId") Long userId) {
        try {
            return ResponseEntity.ok(bookingService.listBookingsForUser(userId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to load user bookings"));
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<?> listPending() {
        try {
            return ResponseEntity.ok(bookingService.listPendingBookings());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to load pending bookings"));
        }
    }

    @GetMapping("/pending/count")
    public ResponseEntity<?> pendingCount() {
        try {
            return ResponseEntity.ok(bookingService.countPendingBookings());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to count pending bookings"));
        }
    }

    private Map<String, String> error(String message) {
        return Map.of("message", message);
    }

    public static class BookingRequest {

        private Long userId;
        private Long resourceId;
        private String startTime;
        private String endTime;
        private String purpose;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getResourceId() {
            return resourceId;
        }

        public void setResourceId(Long resourceId) {
            this.resourceId = resourceId;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public String getPurpose() {
            return purpose;
        }

        public void setPurpose(String purpose) {
            this.purpose = purpose;
        }
    }
}
