package com.college.ara.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.ara.config.RoomCatalog;
import com.college.ara.model.Allocation;
import com.college.ara.model.Booking;
import com.college.ara.model.BookingStatus;
import com.college.ara.model.Resource;
import com.college.ara.model.ResourceType;
import com.college.ara.repository.AllocationRepository;
import com.college.ara.repository.BookingRepository;
import com.college.ara.repository.ResourceRepository;

@Service
public class ResourceService {

    private static final DateTimeFormatter SLOT_TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private final ResourceRepository resourceRepository;
    private final BookingRepository bookingRepository;
    private final AllocationRepository allocationRepository;

    public ResourceService(
            ResourceRepository resourceRepository,
            BookingRepository bookingRepository,
            AllocationRepository allocationRepository) {
        this.resourceRepository = resourceRepository;
        this.bookingRepository = bookingRepository;
        this.allocationRepository = allocationRepository;
    }

    @Transactional
    public Resource createResource(Resource resource) {
        return resourceRepository.save(resource);
    }

    @Transactional(readOnly = true)
    public Resource getResource(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Resource> listResources() {
        return resourceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Resource> listManagedResources() {
        return resourceRepository.findAll().stream()
                .filter(Resource::isActive)
                .filter(resource -> RoomCatalog.MANAGED_ROOM_CODE_SET.contains(resource.getResourceCode()))
                .sorted((left, right) -> left.getResourceCode().compareToIgnoreCase(right.getResourceCode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Resource> findFreeResources(LocalDateTime startTime, LocalDateTime endTime, ResourceType resourceType) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Valid start and end time are required");
        }

        return listManagedResources().stream()
                .filter(resource -> resourceType == null || resource.getResourceType() == resourceType)
                .filter(resource -> isFreeForBookings(resource, startTime, endTime))
                .filter(resource -> isFreeForAllocations(resource, startTime, endTime))
                .toList();
    }

    private boolean isFreeForBookings(Resource resource, LocalDateTime startTime, LocalDateTime endTime) {
        List<Booking> bookings = bookingRepository.findAll();
        for (Booking booking : bookings) {
            if (booking.getResource() == null || booking.getResource().getId() == null) {
                continue;
            }
            if (!booking.getResource().getId().equals(resource.getId())) {
                continue;
            }
            if (!blocksScheduling(booking.getStatus())) {
                continue;
            }
            if (startTime.isBefore(booking.getEndTime()) && endTime.isAfter(booking.getStartTime())) {
                return false;
            }
        }
        return true;
    }

    private boolean isFreeForAllocations(Resource resource, LocalDateTime startTime, LocalDateTime endTime) {
        String dayLabel = startTime.getDayOfWeek().name().substring(0, 1)
                + startTime.getDayOfWeek().name().substring(1).toLowerCase(Locale.ROOT);

        for (Allocation allocation : allocationRepository.findAll()) {
            if (allocation.getRoom() == null || allocation.getRoom().getId() == null) {
                continue;
            }
            if (!allocation.getRoom().getId().equals(resource.getId())) {
                continue;
            }
            if (!dayLabel.equalsIgnoreCase(allocation.getDay())) {
                continue;
            }

            String[] slotParts = allocation.getTime().split(" - ");
            if (slotParts.length != 2) {
                continue;
            }

            LocalTime slotStart = LocalTime.parse(slotParts[0].trim(), SLOT_TIME_FORMAT);
            LocalTime slotEnd = LocalTime.parse(slotParts[1].trim(), SLOT_TIME_FORMAT);
            LocalTime requestedStart = startTime.toLocalTime();
            LocalTime requestedEnd = endTime.toLocalTime();

            if (requestedStart.isBefore(slotEnd) && requestedEnd.isAfter(slotStart)) {
                return false;
            }
        }
        return true;
    }

    private boolean blocksScheduling(BookingStatus status) {
        return status == BookingStatus.PENDING || status == BookingStatus.APPROVED;
    }
}
