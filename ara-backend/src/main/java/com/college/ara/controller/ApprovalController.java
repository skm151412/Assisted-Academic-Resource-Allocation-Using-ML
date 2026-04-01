package com.college.ara.controller;

import com.college.ara.model.Approval;
import com.college.ara.model.Booking;
import com.college.ara.model.User;
import com.college.ara.service.ApprovalService;
import com.college.ara.service.UserService;
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
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final UserService userService;

    public ApprovalController(ApprovalService approvalService, UserService userService) {
        this.approvalService = approvalService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            List<Booking> pendingApprovals = approvalService.listPendingApprovals();
            return ResponseEntity.ok(pendingApprovals);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to load approvals"));
        }
    }

    @PostMapping("/{bookingId}/approve")
    public ResponseEntity<?> approve(@PathVariable("bookingId") Long bookingId, @RequestBody ApprovalRequest request) {
        try {
            if (request == null || request.getApproverId() == null) {
                return ResponseEntity.badRequest().body(error("Approver is required"));
            }
            User approver = userService.getUser(request.getApproverId());
            Approval approval = approvalService.approve(bookingId, approver, request.getRemarks());
            return ResponseEntity.ok(approval);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to approve booking"));
        }
    }

    @PostMapping("/{bookingId}/reject")
    public ResponseEntity<?> reject(@PathVariable("bookingId") Long bookingId, @RequestBody ApprovalRequest request) {
        try {
            if (request == null || request.getApproverId() == null) {
                return ResponseEntity.badRequest().body(error("Approver is required"));
            }
            User approver = userService.getUser(request.getApproverId());
            Approval approval = approvalService.reject(bookingId, approver, request.getRemarks());
            return ResponseEntity.ok(approval);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unable to reject booking"));
        }
    }

    private Map<String, String> error(String message) {
        return Map.of("message", message);
    }

    public static class ApprovalRequest {

        private Long approverId;
        private String remarks;

        public Long getApproverId() {
            return approverId;
        }

        public void setApproverId(Long approverId) {
            this.approverId = approverId;
        }

        public String getRemarks() {
            return remarks;
        }

        public void setRemarks(String remarks) {
            this.remarks = remarks;
        }
    }
}
