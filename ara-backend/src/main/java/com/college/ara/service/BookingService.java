package com.college.ara.service;

import com.college.ara.model.Booking;
import com.college.ara.model.BookingStatus;
import com.college.ara.model.Resource;
import com.college.ara.model.User;
import com.college.ara.repository.BookingRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;

    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public Booking requestBooking(User user, Resource resource, LocalDateTime start, LocalDateTime end, String purpose) {
        validateBookingInput(user, resource, start, end, purpose);

        List<Booking> existingBookings = bookingRepository.findAll();
        for (Booking existing : existingBookings) {
            if (!existing.getResource().getId().equals(resource.getId())) {
                continue;
            }
            if (!blocksScheduling(existing.getStatus())) {
                continue;
            }
            if (overlaps(start, end, existing.getStartTime(), existing.getEndTime())) {
                throw new IllegalArgumentException("Time slot conflicts with existing booking");
            }
        }

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setResource(resource);
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setPurpose(purpose);
        booking.setStatus(BookingStatus.PENDING);
        return bookingRepository.save(booking);
    }

    @Transactional(readOnly = true)
    public List<Booking> listBookings() {
        return bookingRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Booking> listBookingsForUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User is required");
        }
        return bookingRepository.findByUser_IdOrderByRequestedAtDesc(userId);
    }

	@Transactional(readOnly = true)
	public List<Booking> listPendingBookings() {
		return bookingRepository.findByStatus(BookingStatus.PENDING);
	}

    @Transactional(readOnly = true)
    public long countPendingBookings() {
        return bookingRepository.countByStatus(BookingStatus.PENDING);
    }

    private void validateBookingInput(User user, Resource resource, LocalDateTime start, LocalDateTime end, String purpose) {
        if (user == null || resource == null) {
            throw new IllegalArgumentException("User and resource are required");
        }
        if (!user.isActive()) {
            throw new IllegalArgumentException("User is not active");
        }
        if (!resource.isActive()) {
            throw new IllegalArgumentException("Resource is not active");
        }
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("Invalid time range");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("Purpose is required");
        }
    }

    private boolean blocksScheduling(BookingStatus status) {
        return status == BookingStatus.PENDING || status == BookingStatus.APPROVED;
    }

    private boolean overlaps(LocalDateTime startA, LocalDateTime endA, LocalDateTime startB, LocalDateTime endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }
}
