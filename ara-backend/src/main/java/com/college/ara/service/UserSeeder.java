package com.college.ara.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.college.ara.model.Booking;
import com.college.ara.model.BookingStatus;
import com.college.ara.model.Resource;
import com.college.ara.model.ResourceType;
import com.college.ara.model.User;
import com.college.ara.model.UserRole;
import com.college.ara.repository.BookingRepository;
import com.college.ara.repository.ResourceRepository;
import com.college.ara.repository.UserRepository;

@Component
public class UserSeeder implements ApplicationListener<ContextRefreshedEvent> {
    private static final List<String> LAB_ROOM_CODES = List.of("H1-01", "H1-02", "H1-03", "H1-04");
    private static final List<String> CLASSROOM_ROOM_CODES = List.of("H1-17", "H1-18", "H1-19", "H1-22", "H1-23", "H1-25", "H1-26");
    private static final Set<String> ALLOWED_ROOM_CODES = Set.of(
            "H1-01", "H1-02", "H1-03", "H1-04",
            "H1-17", "H1-18", "H1-19", "H1-22", "H1-23", "H1-25", "H1-26");

    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final BookingRepository bookingRepository;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public UserSeeder(
            UserRepository userRepository,
            ResourceRepository resourceRepository,
            BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.resourceRepository = resourceRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    @Transactional
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (initialized.getAndSet(true)) {
            return;
        }

        upsertUser("admin", "admin123", "Administrator", UserRole.ADMIN, "Administration", "Approvals, Resources, Scheduling");
        User faculty = upsertUser("faculty", "faculty123", "Dr Suhma Rani", UserRole.FACULTY, "CSE", "MO, Timetable Review");
        upsertUser("sree.lakshmi", "sree.lakshmi", "Sree Lakshmi", UserRole.FACULTY, "CSE", "FSAD / PFSD");
        upsertUser("anuradha", "anuradha", "Dr Anuradha", UserRole.FACULTY, "CSE", "FSAD / PFSD");

        syncManagedResources();
        Resource classroom = resourceRepository.findByResourceCode("H1-17")
                .orElseGet(() -> upsertResource("H1-17", "H1-17 Classroom", ResourceType.CLASSROOM, 60, "H1 Block"));
        migrateLegacyBookings(classroom);

        if (bookingRepository.count() == 0) {
            Booking pending = new Booking();
            pending.setUser(faculty);
            pending.setResource(classroom);
            pending.setPurpose("Data Structures lab session");
            pending.setStartTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
            pending.setEndTime(LocalDateTime.now().plusDays(1).withHour(12).withMinute(0).withSecond(0).withNano(0));
            pending.setStatus(BookingStatus.PENDING);
            bookingRepository.save(pending);

            Booking approved = new Booking();
            approved.setUser(faculty);
            approved.setResource(classroom);
            approved.setPurpose("Faculty meeting");
            approved.setStartTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0));
            approved.setEndTime(LocalDateTime.now().plusDays(2).withHour(15).withMinute(30).withSecond(0).withNano(0));
            approved.setStatus(BookingStatus.APPROVED);
            approved.setApprovedAt(LocalDateTime.now());
            bookingRepository.save(approved);
        }
    }

    private void migrateLegacyBookings(Resource fallbackResource) {
        for (Booking booking : bookingRepository.findAll()) {
            if (booking.getResource() == null || booking.getResource().isActive()) {
                continue;
            }
            booking.setResource(fallbackResource);
            bookingRepository.save(booking);
        }
    }

    private User upsertUser(String username, String password, String fullName, UserRole role, String department, String subjectsHandled) {
        User user = userRepository.findByUsername(username).orElseGet(User::new);
        user.setFullName(fullName);
        user.setUsername(username);
        user.setPassword(password);
        user.setRole(role);
        user.setDepartment(department);
        user.setSubjectsHandled(subjectsHandled);
        user.setActive(true);
        return userRepository.save(user);
    }

    private void syncManagedResources() {
        for (String code : LAB_ROOM_CODES) {
            upsertResource(code, code + " Lab", ResourceType.LAB, 60, "H1 Block");
        }
        for (String code : CLASSROOM_ROOM_CODES) {
            upsertResource(code, code + " Classroom", ResourceType.CLASSROOM, 60, "H1 Block");
        }

        for (Resource resource : resourceRepository.findAll()) {
            boolean shouldBeActive = ALLOWED_ROOM_CODES.contains(resource.getResourceCode());
            if (resource.isActive() != shouldBeActive) {
                resource.setActive(shouldBeActive);
                resourceRepository.save(resource);
            }
        }
    }

    private Resource upsertResource(String code, String name, ResourceType type, int capacity, String location) {
        Resource resource = resourceRepository.findByResourceCode(code).orElseGet(Resource::new);
        resource.setResourceCode(code);
        resource.setResourceName(name);
        resource.setResourceType(type);
        resource.setCapacity(capacity);
        resource.setLocation(location);
        resource.setActive(true);
        return resourceRepository.save(resource);
    }
}
