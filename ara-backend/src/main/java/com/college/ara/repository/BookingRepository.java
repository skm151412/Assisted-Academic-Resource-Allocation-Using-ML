package com.college.ara.repository;

import com.college.ara.model.Booking;
import com.college.ara.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	long countByStatus(BookingStatus status);

	List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByUser_IdOrderByRequestedAtDesc(Long userId);
}
