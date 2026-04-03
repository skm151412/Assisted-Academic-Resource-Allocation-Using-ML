package com.college.ara.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.ara.model.Approval;
import com.college.ara.model.ApprovalDecision;
import com.college.ara.model.Booking;
import com.college.ara.model.BookingStatus;
import com.college.ara.model.User;
import com.college.ara.repository.ApprovalRepository;
import com.college.ara.repository.BookingRepository;

@Service
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final BookingRepository bookingRepository;

    public ApprovalService(ApprovalRepository approvalRepository, BookingRepository bookingRepository) {
        this.approvalRepository = approvalRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public Approval approve(Long bookingId, User approver, String remarks) {
        Booking booking = findBooking(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalArgumentException("Only pending bookings can be approved");
        }
        booking.setStatus(BookingStatus.APPROVED);
        booking.setApprovedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        Approval approval = new Approval();
        approval.setBooking(booking);
        approval.setApprover(approver);
        approval.setDecision(ApprovalDecision.APPROVED);
        approval.setDecisionAt(LocalDateTime.now());
        approval.setRemarks(remarks);
        return approvalRepository.save(approval);
    }

    @Transactional
    public Approval reject(Long bookingId, User approver, String remarks) {
        Booking booking = findBooking(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalArgumentException("Only pending bookings can be rejected");
        }
        booking.setStatus(BookingStatus.REJECTED);
        bookingRepository.save(booking);

        Approval approval = new Approval();
        approval.setBooking(booking);
        approval.setApprover(approver);
        approval.setDecision(ApprovalDecision.REJECTED);
        approval.setDecisionAt(LocalDateTime.now());
        approval.setRemarks(remarks);
        return approvalRepository.save(approval);
    }

    @Transactional(readOnly = true)
    public List<Booking> listPendingApprovals() {
        return bookingRepository.findByStatus(BookingStatus.PENDING);
    }

    private Booking findBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
    }
}
