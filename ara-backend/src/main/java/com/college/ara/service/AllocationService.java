package com.college.ara.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.ara.config.RoomCatalog;
import com.college.ara.model.Allocation;
import com.college.ara.model.Resource;
import com.college.ara.model.ResourceType;
import com.college.ara.repository.AllocationRepository;
import com.college.ara.repository.ResourceRepository;

@Service
public class AllocationService {

    private static final String SECOND_YEAR = "II Year";
    private static final List<String> TIME_SLOTS = List.of(
            "08:15 AM - 09:05 AM",
            "09:05 AM - 09:55 AM",
            "10:10 AM - 11:00 AM",
            "11:00 AM - 11:50 AM",
            "11:50 AM - 12:45 PM",
            "01:30 PM - 02:20 PM",
            "02:20 PM - 03:10 PM",
            "03:10 PM - 04:00 PM");
    private static final List<String> LAB_ROOM_CODES = RoomCatalog.LAB_ROOM_CODES;
    private static final List<String> CLASSROOM_ROOM_CODES = RoomCatalog.CLASSROOM_ROOM_CODES;
    private static final List<String> DEFAULT_SECOND_YEAR_SECTIONS = List.of("E1", "E2", "E3", "A1", "A2", "A3", "A4", "A5", "A6");
    private static final Set<String> DEFAULT_SECOND_YEAR_EXCLUDED_DAYS = Set.of("WEDNESDAY");
    private static final Set<String> NO_ROOM_ACTIVITY_SUBJECT_KEYS = Set.of("CRT", "GLOBAL CERTIFICATION", "GLOBAL LOGIC", "LIBRARY", "SPORTS");
    private static final Map<String, Integer> DAY_ORDER = Map.of(
            "MONDAY", 1,
            "TUESDAY", 2,
            "WEDNESDAY", 3,
            "THURSDAY", 4,
            "FRIDAY", 5,
            "SATURDAY", 6);
    private static final Map<String, String> DAY_LABELS = Map.of(
            "MONDAY", "Monday",
            "TUESDAY", "Tuesday",
            "WEDNESDAY", "Wednesday",
            "THURSDAY", "Thursday",
            "FRIDAY", "Friday",
            "SATURDAY", "Saturday");
    private static final List<String> DAYS = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
    private static final int MAX_SUBJECTS_PER_DAY = 8;
    private static final int MAX_CONSECUTIVE_SAME_SUBJECT = 4;
    private static final int MAX_GENERATION_ATTEMPTS = 1200;
    private static final Map<String, Map<String, String>> PDF_FACULTY_BY_SECTION = buildPdfFacultyBySection();

    private final AllocationRepository allocationRepository;
    private final ResourceRepository resourceRepository;

    public AllocationService(AllocationRepository allocationRepository, ResourceRepository resourceRepository) {
        this.allocationRepository = allocationRepository;
        this.resourceRepository = resourceRepository;
    }

    @Transactional
    public List<Allocation> generateAllocations(Map<String, Object> request) {
        allocationRepository.deleteAll();
        ensureResourcesExist();

        List<Resource> allResources = resourceRepository.findAll();
        List<Resource> labRooms = filterAllowedRooms(allResources, LAB_ROOM_CODES);
        List<Resource> classroomRooms = filterAllowedRooms(allResources, CLASSROOM_ROOM_CODES);
        if (labRooms.isEmpty() || classroomRooms.isEmpty()) {
            throw new IllegalStateException("Required room inventory is unavailable");
        }

        Map<String, YearConfigInput> yearConfigs = parseYearConfigs(request);
        List<TimetableEntryInput> entries = parseCustomEntries(request);
        if (entries.isEmpty()) {
            entries = buildDefaultSecondYearEntries(yearConfigs);
        } else {
            entries = applyYearConfigRules(entries, yearConfigs);
            validateDuplicateEntries(entries);
            return allocationRepository.saveAll(generateFromCustomEntries(entries, yearConfigs, classroomRooms, labRooms));
        }

        if (entries.isEmpty()) {
            throw new IllegalArgumentException("No timetable entries available for allocation");
        }

        entries = entries.stream()
                .filter(entry -> DAY_ORDER.containsKey(normalizeDay(entry.day)))
                .filter(entry -> TIME_SLOTS.contains(normalizeTime(entry.time)))
                .toList();

        if (entries.isEmpty()) {
            throw new IllegalArgumentException("No valid entries with supported days and time slots");
        }

        Map<String, SectionDemand> sectionDemandMap = buildSectionDemands(entries, yearConfigs);
        if (sectionDemandMap.isEmpty()) {
            throw new IllegalArgumentException("No section demand found from timetable entries");
        }

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            SchedulerState state = new SchedulerState(classroomRooms, labRooms, attempt);
            if (!scheduleAllSections(sectionDemandMap, state)) {
                continue;
            }

            if (!validateSchedules(sectionDemandMap, state)) {
                continue;
            }

            return allocationRepository.saveAll(toAllocations(state));
        }

        throw new IllegalStateException("Unable to generate a valid timetable with LTPS constraints");
    }

    @Transactional(readOnly = true)
    public List<Allocation> getAllAllocations() {
        return allocationRepository.findAll().stream()
                .sorted(Comparator
                        .comparingInt((Allocation allocation) -> DAY_ORDER.getOrDefault(normalizeDay(allocation.getDay()), Integer.MAX_VALUE))
                        .thenComparingInt(allocation -> TIME_SLOTS.indexOf(allocation.getTime()))
                        .thenComparing(Allocation::getYearGroup, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Allocation::getSection, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Allocation> getAllocationsForFaculty(String facultyName) {
        if (facultyName == null || facultyName.isBlank()) {
            return Collections.emptyList();
        }

        String normalizedFaculty = facultyName.trim().toLowerCase(Locale.ROOT);
        return getAllAllocations().stream()
                .filter(allocation -> allocation.getFacultyName() != null)
                .filter(allocation -> allocation.getFacultyName().trim().toLowerCase(Locale.ROOT).contains(normalizedFaculty))
                .toList();
    }

    private boolean scheduleAllSections(Map<String, SectionDemand> sectionDemandMap, SchedulerState state) {
        List<SectionDemand> orderedSections = sectionDemandMap.values().stream()
                .sorted(Comparator.comparing((SectionDemand section) -> normalizeYearGroup(section.yearGroup))
                        .thenComparing(section -> section.section))
                .toList();

        for (SectionDemand sectionDemand : orderedSections) {
            SectionSchedule schedule = new SectionSchedule(sectionDemand.yearGroup, sectionDemand.section);

            if (!placeUnits(schedule, sectionDemand, sectionDemand.practicalUnits, 2, state)) {
                return false;
            }
            if (!placeUnits(schedule, sectionDemand, sectionDemand.lectureUnits, 1, state)) {
                return false;
            }
            if (!placeUnits(schedule, sectionDemand, sectionDemand.tutorialUnits, 1, state)) {
                return false;
            }

            if (!allSlotsFilled(schedule, sectionDemand.activeDayIndexes)) {
                return false;
            }

            state.sectionSchedules.put(sectionKey(schedule.yearGroup, schedule.section), schedule);
            state.sectionDemands.put(sectionKey(schedule.yearGroup, schedule.section), sectionDemand);
        }

        return true;
    }

    private boolean placeUnits(
            SectionSchedule schedule,
            SectionDemand sectionDemand,
            List<DemandUnit> units,
            int blockSize,
            SchedulerState state) {

        List<DemandUnit> placementOrder = new ArrayList<>(units);
        placementOrder.sort(Comparator.comparingInt((DemandUnit unit) -> unit.remainingSlots).reversed()
                .thenComparing(unit -> unit.subjectKey));

        for (DemandUnit unit : placementOrder) {
            while (unit.remainingSlots > 0) {
                if (unit.remainingSlots < blockSize) {
                    return false;
                }

                CandidatePlacement placement = findBestPlacement(schedule, sectionDemand, unit, blockSize, state);
                if (placement == null) {
                    return false;
                }

                place(schedule, unit, placement, blockSize, state);
            }
        }

        return true;
    }

    private CandidatePlacement findBestPlacement(SectionSchedule schedule, SectionDemand sectionDemand, DemandUnit unit, int blockSize, SchedulerState state) {
        List<CandidatePlacement> candidates = new ArrayList<>();
        for (int day : sectionDemand.activeDayIndexes) {
            for (int slot = 0; slot <= TIME_SLOTS.size() - blockSize; slot++) {
                CandidatePlacement candidate = buildCandidate(schedule, unit, day, slot, blockSize, state);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator
                .comparingInt((CandidatePlacement candidate) -> candidate.subjectDayCount)
                .thenComparingInt(candidate -> candidate.dayFillCount)
                .thenComparingInt(candidate -> candidate.dayUniqueSubjects)
                .thenComparingInt(candidate -> candidate.slot));

        int pickIndex = state.random.nextInt(Math.min(3, candidates.size()));
        return candidates.get(pickIndex);
    }

    private CandidatePlacement buildCandidate(
            SectionSchedule schedule,
            DemandUnit unit,
            int day,
            int slot,
            int blockSize,
            SchedulerState state) {

        for (int offset = 0; offset < blockSize; offset++) {
            if (schedule.grid[day][slot + offset] != null) {
                return null;
            }
        }

        Set<String> uniqueSubjects = subjectsForDay(schedule, day);
        if (!uniqueSubjects.contains(unit.subjectKey) && uniqueSubjects.size() >= MAX_SUBJECTS_PER_DAY) {
            return null;
        }

        if (wouldBreakConsecutiveLimit(schedule, unit.subjectKey, day, slot, blockSize)) {
            return null;
        }

        List<String> facultyTokens = facultyTokens(unit.facultyName);
        for (int offset = 0; offset < blockSize; offset++) {
            int targetSlot = slot + offset;
            if (hasFacultyConflict(facultyTokens, day, targetSlot, state)) {
                return null;
            }
        }

        Resource room = pickRoom(unit, day, slot, blockSize, state);
        if (requiresRoom(unit) && room == null) {
            return null;
        }

        return new CandidatePlacement(day, slot, room, countSubjectInDay(schedule, unit.subjectKey, day), countFilledInDay(schedule, day), uniqueSubjects.size());
    }

    private void place(SectionSchedule schedule, DemandUnit unit, CandidatePlacement placement, int blockSize, SchedulerState state) {
        SlotAssignment assignment = new SlotAssignment(unit.subjectLabel, unit.subjectKey, unit.facultyName, unit.allocationType, unit.ltpsType, placement.room, "Auto-generated valid timetable");
        List<String> facultyTokens = facultyTokens(unit.facultyName);

        for (int offset = 0; offset < blockSize; offset++) {
            int targetSlot = placement.slot + offset;
            schedule.grid[placement.day][targetSlot] = assignment;
            if (!facultyTokens.isEmpty()) {
                state.facultyUsage.computeIfAbsent(usageKey(placement.day, targetSlot), key -> new HashSet<>()).addAll(facultyTokens);
            }
            if (placement.room != null) {
                state.roomUsage.computeIfAbsent(usageKey(placement.day, targetSlot), key -> new HashSet<>()).add(placement.room.getId());
            }
        }

        unit.remainingSlots -= blockSize;
    }

    private boolean validateSchedules(Map<String, SectionDemand> sectionDemandMap, SchedulerState state) {
        if (state.sectionSchedules.size() != sectionDemandMap.size()) {
            return false;
        }

        Map<String, Set<String>> facultySeen = new HashMap<>();
        Map<String, Set<Long>> roomSeen = new HashMap<>();

        for (SectionSchedule schedule : state.sectionSchedules.values()) {
            SectionDemand demand = sectionDemandMap.get(sectionKey(schedule.yearGroup, schedule.section));
            if (demand == null) {
                return false;
            }

            if (!allSlotsFilled(schedule, demand.activeDayIndexes)) {
                return false;
            }

            Map<String, Integer> scheduledCounts = new HashMap<>();
            for (int day : demand.activeDayIndexes) {
                Set<String> daySubjects = new HashSet<>();
                int runLength = 0;
                String runSubject = "";

                for (int slot = 0; slot < TIME_SLOTS.size(); slot++) {
                    SlotAssignment assignment = schedule.grid[day][slot];
                    if (assignment == null) {
                        return false;
                    }

                    daySubjects.add(assignment.subjectKey);
                    String countKey = demandCountKey(assignment.subjectKey, assignment.ltpsType);
                    scheduledCounts.put(countKey, scheduledCounts.getOrDefault(countKey, 0) + 1);

                    if (assignment.subjectKey.equals(runSubject)) {
                        runLength++;
                    } else {
                        runSubject = assignment.subjectKey;
                        runLength = 1;
                    }

                    if (runLength > MAX_CONSECUTIVE_SAME_SUBJECT) {
                        return false;
                    }

                    String usageKey = usageKey(day, slot);
                    List<String> facultyTokens = facultyTokens(assignment.facultyName);
                    if (!facultyTokens.isEmpty()) {
                        Set<String> slotFaculty = facultySeen.computeIfAbsent(usageKey, key -> new HashSet<>());
                        for (String faculty : facultyTokens) {
                            if (slotFaculty.contains(faculty)) {
                                return false;
                            }
                            slotFaculty.add(faculty);
                        }
                    }

                    if (assignment.room != null) {
                        Set<Long> slotRooms = roomSeen.computeIfAbsent(usageKey, key -> new HashSet<>());
                        if (slotRooms.contains(assignment.room.getId())) {
                            return false;
                        }
                        slotRooms.add(assignment.room.getId());
                    }
                }

                if (daySubjects.size() > MAX_SUBJECTS_PER_DAY) {
                    return false;
                }

                if (!validatePracticalPairs(schedule, day)) {
                    return false;
                }
            }

            if (!Objects.equals(demand.requiredCounts, scheduledCounts)) {
                return false;
            }
        }

        return true;
    }

    private boolean validatePracticalPairs(SectionSchedule schedule, int day) {
        int slot = 0;
        while (slot < TIME_SLOTS.size()) {
            SlotAssignment assignment = schedule.grid[day][slot];
            if (assignment.ltpsType != LtpsType.PRACTICAL) {
                slot++;
                continue;
            }

            int run = 1;
            while (slot + run < TIME_SLOTS.size()) {
                SlotAssignment next = schedule.grid[day][slot + run];
                if (next.ltpsType == LtpsType.PRACTICAL && next.subjectKey.equals(assignment.subjectKey)) {
                    run++;
                } else {
                    break;
                }
            }

            if (run != 2) {
                return false;
            }
            slot += run;
        }

        return true;
    }

    private List<Allocation> toAllocations(SchedulerState state) {
        List<Allocation> allocations = new ArrayList<>();
        List<SectionSchedule> schedules = state.sectionSchedules.values().stream()
                .sorted(Comparator.comparing((SectionSchedule section) -> normalizeYearGroup(section.yearGroup))
                        .thenComparing(section -> section.section))
                .toList();

        for (SectionSchedule schedule : schedules) {
            SectionDemand demand = state.sectionDemands.get(sectionKey(schedule.yearGroup, schedule.section));
            if (demand == null) {
                continue;
            }
            for (int day : demand.activeDayIndexes) {
                for (int slot = 0; slot < TIME_SLOTS.size(); slot++) {
                    SlotAssignment assignment = schedule.grid[day][slot];
                    if (assignment == null) {
                        continue;
                    }
                    Allocation allocation = new Allocation();
                    allocation.setYearGroup(schedule.yearGroup);
                    allocation.setSection(schedule.section);
                    allocation.setDay(DAYS.get(day));
                    allocation.setTime(TIME_SLOTS.get(slot));
                    allocation.setSubject(assignment.subjectLabel);
                    allocation.setFacultyName(assignment.facultyName);
                    allocation.setAllocationType(assignment.allocationType);
                    allocation.setSourceLabel(assignment.sourceLabel);
                    allocation.setRoom(assignment.room);
                    allocations.add(allocation);
                }
            }
        }

        return allocations;
    }

    private Map<String, SectionDemand> buildSectionDemands(List<TimetableEntryInput> entries, Map<String, YearConfigInput> yearConfigs) {
        Map<String, Map<String, DemandAccumulator>> accumulators = new LinkedHashMap<>();
        Map<String, String> sectionYearGroup = new HashMap<>();

        for (TimetableEntryInput entry : entries) {
            LtpsType ltpsType = resolveLtpsType(entry);
            String subjectKey = subjectKey(entry.subject);
            if (subjectKey.isBlank()) {
                continue;
            }

            String sectionKey = sectionKey(entry.yearGroup, entry.section);
            sectionYearGroup.putIfAbsent(sectionKey, entry.yearGroup);
            Map<String, DemandAccumulator> sectionMap = accumulators.computeIfAbsent(sectionKey, key -> new LinkedHashMap<>());

            String key = demandCountKey(subjectKey, ltpsType);
            DemandAccumulator accumulator = sectionMap.computeIfAbsent(key,
                    ignored -> new DemandAccumulator(subjectKey, normalizeSubjectLabel(entry.subject, ltpsType), entry.facultyName, toAllocationType(ltpsType), ltpsType));
            accumulator.slots += 1;
            if (accumulator.facultyName.isBlank() && !entry.facultyName.isBlank()) {
                accumulator.facultyName = entry.facultyName;
            }
        }

        Map<String, SectionDemand> demands = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, DemandAccumulator>> sectionEntry : accumulators.entrySet()) {
            String sectionKey = sectionEntry.getKey();
            String[] sectionParts = sectionKey.split("\\|", 2);
            String yearGroup = sectionYearGroup.getOrDefault(sectionKey, sectionParts[0]);
            String section = sectionParts[1];
            YearConfigInput yearConfig = yearConfigs.getOrDefault(normalizeYearGroup(yearGroup),
                    new YearConfigInput(yearGroup, Set.of(), Set.of()));
            List<Integer> activeDayIndexes = getActiveDayIndexes(yearConfig.excludedDays);
            if (activeDayIndexes.isEmpty()) {
                throw new IllegalArgumentException("No active days available for " + yearGroup);
            }

            List<DemandUnit> practical = new ArrayList<>();
            List<DemandUnit> lecture = new ArrayList<>();
            List<DemandUnit> tutorial = new ArrayList<>();
            Map<String, Integer> requiredCounts = new HashMap<>();
            int totalSlots = 0;

            for (DemandAccumulator accumulator : sectionEntry.getValue().values()) {
                if (accumulator.ltpsType == LtpsType.PRACTICAL && accumulator.slots % 2 != 0) {
                    throw new IllegalArgumentException("Invalid LTPS count for " + accumulator.subjectLabel + " in " + section + ": P/S requires pairs");
                }

                DemandUnit unit = new DemandUnit(
                        accumulator.subjectKey,
                        accumulator.subjectLabel,
                        accumulator.facultyName,
                        accumulator.allocationType,
                        accumulator.ltpsType,
                        accumulator.slots);

                requiredCounts.put(demandCountKey(unit.subjectKey, unit.ltpsType), unit.remainingSlots);
                totalSlots += unit.remainingSlots;

                if (unit.ltpsType == LtpsType.PRACTICAL) {
                    practical.add(unit);
                } else if (unit.ltpsType == LtpsType.TUTORIAL) {
                    tutorial.add(unit);
                } else {
                    lecture.add(unit);
                }
            }

            int expectedSlots = activeDayIndexes.size() * TIME_SLOTS.size();
            if (totalSlots != expectedSlots) {
                throw new IllegalArgumentException("Invalid LTPS totals for " + section + ": expected " + expectedSlots + " slots, found " + totalSlots);
            }

            demands.put(sectionKey, new SectionDemand(yearGroup, section, practical, lecture, tutorial, requiredCounts, activeDayIndexes));
        }

        return demands;
    }

    private LtpsType resolveLtpsType(TimetableEntryInput entry) {
        String subject = normalizeSubject(entry.subject).toUpperCase(Locale.ROOT);
        if (NO_ROOM_ACTIVITY_SUBJECT_KEYS.contains(subjectKey(entry.subject)) || "ACTIVITY".equalsIgnoreCase(entry.allocationType)) {
            return LtpsType.ACTIVITY;
        }
        if (subject.contains("(P)") || "LAB".equalsIgnoreCase(entry.allocationType)) {
            return LtpsType.PRACTICAL;
        }
        if (subject.contains("(T)") || "TUTORIAL".equalsIgnoreCase(entry.allocationType)) {
            return LtpsType.TUTORIAL;
        }
        return LtpsType.LECTURE;
    }

    private String normalizeSubjectLabel(String subject, LtpsType ltpsType) {
        String normalized = normalizeSubject(subject);
        if (ltpsType == LtpsType.PRACTICAL && !normalized.contains("(P)") && !normalized.contains("(S)")) {
            return normalized + " (P)";
        }
        if (ltpsType == LtpsType.TUTORIAL && !normalized.contains("(T)")) {
            return normalized + " (T)";
        }
        return normalized;
    }

    private String toAllocationType(LtpsType ltpsType) {
        if (ltpsType == LtpsType.ACTIVITY) {
            return "ACTIVITY";
        }
        if (ltpsType == LtpsType.PRACTICAL) {
            return "LAB";
        }
        if (ltpsType == LtpsType.TUTORIAL) {
            return "TUTORIAL";
        }
        return "LECTURE";
    }

    private Resource pickRoom(DemandUnit unit, int day, int slot, int blockSize, SchedulerState state) {
        if (!requiresRoom(unit)) {
            return null;
        }

        List<Resource> pool = unit.ltpsType == LtpsType.PRACTICAL ? state.labRooms : state.classRooms;
        if (pool.isEmpty()) {
            return null;
        }

        for (Resource room : pool) {
            boolean available = true;
            for (int offset = 0; offset < blockSize; offset++) {
                Set<Long> used = state.roomUsage.getOrDefault(usageKey(day, slot + offset), Collections.emptySet());
                if (used.contains(room.getId())) {
                    available = false;
                    break;
                }
            }
            if (available) {
                return room;
            }
        }
        return null;
    }

    private boolean requiresRoom(DemandUnit unit) {
        return unit.ltpsType != LtpsType.ACTIVITY;
    }

    private boolean wouldBreakConsecutiveLimit(SectionSchedule schedule, String subjectKey, int day, int startSlot, int blockSize) {
        String[] row = new String[TIME_SLOTS.size()];
        for (int slot = 0; slot < TIME_SLOTS.size(); slot++) {
            SlotAssignment assignment = schedule.grid[day][slot];
            row[slot] = assignment == null ? "" : assignment.subjectKey;
        }
        for (int offset = 0; offset < blockSize; offset++) {
            row[startSlot + offset] = subjectKey;
        }

        int run = 0;
        String last = "";
        for (String subject : row) {
            if (subject.equals(last) && !subject.isBlank()) {
                run++;
            } else {
                last = subject;
                run = subject.isBlank() ? 0 : 1;
            }
            if (run > MAX_CONSECUTIVE_SAME_SUBJECT) {
                return true;
            }
        }

        return false;
    }

    private boolean hasFacultyConflict(List<String> facultyTokens, int day, int slot, SchedulerState state) {
        if (facultyTokens.isEmpty()) {
            return false;
        }
        Set<String> used = state.facultyUsage.getOrDefault(usageKey(day, slot), Collections.emptySet());
        return facultyTokens.stream().anyMatch(used::contains);
    }

    private Set<String> subjectsForDay(SectionSchedule schedule, int day) {
        Set<String> subjects = new HashSet<>();
        for (int slot = 0; slot < TIME_SLOTS.size(); slot++) {
            SlotAssignment assignment = schedule.grid[day][slot];
            if (assignment != null) {
                subjects.add(assignment.subjectKey);
            }
        }
        return subjects;
    }

    private int countSubjectInDay(SectionSchedule schedule, String subjectKey, int day) {
        int count = 0;
        for (int slot = 0; slot < TIME_SLOTS.size(); slot++) {
            SlotAssignment assignment = schedule.grid[day][slot];
            if (assignment != null && assignment.subjectKey.equals(subjectKey)) {
                count++;
            }
        }
        return count;
    }

    private int countFilledInDay(SectionSchedule schedule, int day) {
        int count = 0;
        for (int slot = 0; slot < TIME_SLOTS.size(); slot++) {
            if (schedule.grid[day][slot] != null) {
                count++;
            }
        }
        return count;
    }

    private boolean allSlotsFilled(SectionSchedule schedule, List<Integer> activeDayIndexes) {
        Set<Integer> activeDays = new HashSet<>(activeDayIndexes);
        for (int day = 0; day < DAYS.size(); day++) {
            for (int slot = 0; slot < TIME_SLOTS.size(); slot++) {
                SlotAssignment assignment = schedule.grid[day][slot];
                if (activeDays.contains(day) && assignment == null) {
                    return false;
                }
                if (!activeDays.contains(day) && assignment != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<String> facultyTokens(String facultyName) {
        if (facultyName == null || facultyName.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(facultyName.trim().toLowerCase(Locale.ROOT));
    }

    private String usageKey(int day, int slot) {
        return day + "|" + slot;
    }

    private String demandCountKey(String subjectKey, LtpsType ltpsType) {
        return subjectKey + "|" + ltpsType.name();
    }

    private String sectionKey(String yearGroup, String section) {
        return normalizeYearGroup(yearGroup) + "|" + section.trim().toUpperCase(Locale.ROOT);
    }

    private List<Integer> getActiveDayIndexes(Set<String> excludedDays) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < DAYS.size(); index++) {
            String normalizedDay = normalizeDay(DAYS.get(index));
            if (!excludedDays.contains(normalizedDay)) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private List<TimetableEntryInput> buildDefaultSecondYearEntries(Map<String, YearConfigInput> yearConfigs) {
        YearConfigInput secondYearConfig = yearConfigs.getOrDefault(
                normalizeYearGroup(SECOND_YEAR),
                new YearConfigInput(SECOND_YEAR, new HashSet<>(DEFAULT_SECOND_YEAR_SECTIONS), new HashSet<>(DEFAULT_SECOND_YEAR_EXCLUDED_DAYS)));

        Set<String> allowedSections = secondYearConfig.sections.isEmpty()
                ? new HashSet<>(DEFAULT_SECOND_YEAR_SECTIONS)
                : secondYearConfig.sections;
        Set<String> excludedDays = secondYearConfig.excludedDays.isEmpty()
                ? new HashSet<>(DEFAULT_SECOND_YEAR_EXCLUDED_DAYS)
                : secondYearConfig.excludedDays;

        List<TimetableEntryInput> entries = new ArrayList<>();

        addPdfDay(entries, allowedSections, excludedDays, "E1", "Monday", "DAA (L)", "DAA (L)", "MO", "MO", "PFSD (P)", "PFSD (P)", "FSAD (S)", "FSAD (S)");
        addPdfDay(entries, allowedSections, excludedDays, "E1", "Tuesday", "FSAD", "FSAD", "GLOBAL CERTIFICATION", "GLOBAL CERTIFICATION", "MO", "MO", "DAA (S)", "DAA (S)");
        addPdfDay(entries, allowedSections, excludedDays, "E1", "Wednesday", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT");
        addPdfDay(entries, allowedSections, excludedDays, "E1", "Thursday", "CN", "CN", "SPORTS", "SPORTS", "GLOBAL LOGIC", "GLOBAL LOGIC", "CTI (P)", "CTI (P)");
        addPdfDay(entries, allowedSections, excludedDays, "E1", "Friday", "CTI", "CTI", "PFSD", "FSAD", "DAA (P)", "DAA (P)", "LIBRARY", "LIBRARY");
        addPdfDay(entries, allowedSections, excludedDays, "E1", "Saturday", "DAA (L)", "PFSD", "PFSD", "CN", "FSAD (S)", "FSAD (S)", "CN (P)", "CN (P)");

        addPdfDay(entries, allowedSections, excludedDays, "E2", "Monday", "PFSD", "PFSD", "FSAD (S)", "FSAD (S)", "NLP", "NLP", "SPORTS", "SPORTS");
        addPdfDay(entries, allowedSections, excludedDays, "E2", "Tuesday", "FSAD", "FSAD", "GLOBAL CERTIFICATION", "GLOBAL CERTIFICATION", "PFSD (P)", "PFSD (P)", "NLP (P)", "NLP (P)");
        addPdfDay(entries, allowedSections, excludedDays, "E2", "Wednesday", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT");
        addPdfDay(entries, allowedSections, excludedDays, "E2", "Thursday", "MO", "MO", "DAA", "DAA", "GLOBAL LOGIC", "GLOBAL LOGIC", "LIBRARY", "LIBRARY");
        addPdfDay(entries, allowedSections, excludedDays, "E2", "Friday", "DAA (S)", "DAA (S)", "CN", "PFSD", "FSAD (S)", "FSAD (S)", "CN (P)", "CN (P)");
        addPdfDay(entries, allowedSections, excludedDays, "E2", "Saturday", "CN", "FSAD", "DAA", "CN", "MO", "MO", "DAA (P)", "DAA (P)");

        addPdfDay(entries, allowedSections, excludedDays, "E3", "Monday", "CN", "CN", "MO", "MO", "DAA (P)", "DAA (P)", "FSAD (S)", "FSAD (S)");
        addPdfDay(entries, allowedSections, excludedDays, "E3", "Tuesday", "NLP (P)", "NLP (P)", "GLOBAL CERTIFICATION", "GLOBAL CERTIFICATION", "DAA", "CN", "LIBRARY", "LIBRARY");
        addPdfDay(entries, allowedSections, excludedDays, "E3", "Wednesday", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT");
        addPdfDay(entries, allowedSections, excludedDays, "E3", "Thursday", "PFSD", "FSAD", "NLP", "NLP", "GLOBAL LOGIC", "GLOBAL LOGIC", "DAA (S)", "DAA (S)");
        addPdfDay(entries, allowedSections, excludedDays, "E3", "Friday", "CN (P)", "CN (P)", "FSAD", "FSAD", "PFSD", "PFSD", "SPORTS", "SPORTS");
        addPdfDay(entries, allowedSections, excludedDays, "E3", "Saturday", "DAA", "DAA", "FSAD (S)", "FSAD (S)", "MO", "MO", "PFSD (P)", "PFSD (P)");

        addPdfDay(entries, allowedSections, excludedDays, "A1", "Monday", "MO", "MO", "CN (P)", "CN (P)", "SPORTS", "SPORTS", "CTI (P)", "CTI (P)");
        addPdfDay(entries, allowedSections, excludedDays, "A1", "Tuesday", "DAA", "DAA", "GLOBAL CERTIFICATION", "GLOBAL CERTIFICATION", "PDNC (P)", "PDNC (P)", "FSAD", "FSAD");
        addPdfDay(entries, allowedSections, excludedDays, "A1", "Wednesday", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT");
        addPdfDay(entries, allowedSections, excludedDays, "A1", "Thursday", "CTI", "CTI", "FSAD (S)", "FSAD (S)", "GLOBAL LOGIC", "GLOBAL LOGIC", "MO", "MO");
        addPdfDay(entries, allowedSections, excludedDays, "A1", "Friday", "PDNC", "PDNC", "DAA", "CN", "FSAD (S)", "FSAD (S)", "DAA (S)", "DAA (S)");
        addPdfDay(entries, allowedSections, excludedDays, "A1", "Saturday", "FSAD", "PDNC", "CN", "CN", "DAA (P)", "DAA (P)", "LIBRARY", "LIBRARY");

        addPdfDay(entries, allowedSections, excludedDays, "A2", "Monday", "PDNC", "PDNC", "FSAD (S)", "FSAD (S)", "DAA", "DAA", "FCSHPC", "FCSHPC");
        addPdfDay(entries, allowedSections, excludedDays, "A2", "Tuesday", "FSAD", "FSAD", "GLOBAL CERTIFICATION", "GLOBAL CERTIFICATION", "SPORTS", "SPORTS", "DAA (S)", "DAA (S)");
        addPdfDay(entries, allowedSections, excludedDays, "A2", "Wednesday", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT");
        addPdfDay(entries, allowedSections, excludedDays, "A2", "Thursday", "FSAD", "PDNC", "MO", "MO", "GLOBAL LOGIC", "GLOBAL LOGIC", "CN (P)", "CN (P)");
        addPdfDay(entries, allowedSections, excludedDays, "A2", "Friday", "DAA", "CN", "PDNC (P)", "PDNC (P)", "FCSHPC (P)", "FCSHPC (P)", "LIBRARY", "LIBRARY");
        addPdfDay(entries, allowedSections, excludedDays, "A2", "Saturday", "DAA (P)", "DAA (P)", "MO", "MO", "CN", "CN", "FSAD (S)", "FSAD (S)");

        addPdfDay(entries, allowedSections, excludedDays, "A3", "Monday", "DAA", "DAA", "PDNC (P)", "PDNC (P)", "FSAD (S)", "FSAD (S)", "DAA", "LIBRARY");
        addPdfDay(entries, allowedSections, excludedDays, "A3", "Tuesday", "PDNC", "PDNC", "GLOBAL CERTIFICATION", "GLOBAL CERTIFICATION", "MO", "MO", "SPORTS", "SPORTS");
        addPdfDay(entries, allowedSections, excludedDays, "A3", "Wednesday", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT");
        addPdfDay(entries, allowedSections, excludedDays, "A3", "Thursday", "FCSHPC", "FCSHPC", "CN", "CN", "GLOBAL LOGIC", "GLOBAL LOGIC", "PDNC", "LIBRARY");
        addPdfDay(entries, allowedSections, excludedDays, "A3", "Friday", "DAA (P)", "DAA (P)", "FSAD (S)", "FSAD (S)", "CN (P)", "CN (P)", "FSAD", "FSAD");
        addPdfDay(entries, allowedSections, excludedDays, "A3", "Saturday", "FSAD", "CN", "MO", "MO", "FCSHPC (P)", "FCSHPC (P)", "DAA (S)", "DAA (S)");

        addPdfDay(entries, allowedSections, excludedDays, "A4", "Monday", "MO", "MO", "FSAD", "PDNC", "CN", "CN", "PDNC (P)", "PDNC (P)");
        addPdfDay(entries, allowedSections, excludedDays, "A4", "Tuesday", "FCSHPC", "FCSHPC", "GLOBAL CERTIFICATION", "GLOBAL CERTIFICATION", "CN (P)", "CN (P)", "FSAD (S)", "FSAD (S)");
        addPdfDay(entries, allowedSections, excludedDays, "A4", "Wednesday", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT");
        addPdfDay(entries, allowedSections, excludedDays, "A4", "Thursday", "DAA", "DAA", "PDNC", "PDNC", "GLOBAL LOGIC", "GLOBAL LOGIC", "FSAD", "FSAD");
        addPdfDay(entries, allowedSections, excludedDays, "A4", "Friday", "FCSHPC", "FCSHPC", "DAA (P)", "DAA (P)", "LIBRARY", "LIBRARY", "FSAD (S)", "FSAD (S)");
        addPdfDay(entries, allowedSections, excludedDays, "A4", "Saturday", "DAA", "CN", "SPORTS", "SPORTS", "DAA (S)", "DAA (S)", "MO", "MO");

        addPdfDay(entries, allowedSections, excludedDays, "A5", "Monday", "FCSHPC", "FCSHPC", "SPORTS", "SPORTS", "DAA (S)", "DAA (S)", "MO", "MO");
        addPdfDay(entries, allowedSections, excludedDays, "A5", "Tuesday", "CN", "CN", "GLOBAL CERTIFICATION", "GLOBAL CERTIFICATION", "FSAD (S)", "FSAD (S)", "DAA", "PDNC");
        addPdfDay(entries, allowedSections, excludedDays, "A5", "Wednesday", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT");
        addPdfDay(entries, allowedSections, excludedDays, "A5", "Thursday", "DAA (P)", "DAA (P)", "FSAD", "FSAD", "GLOBAL LOGIC", "GLOBAL LOGIC", "FSAD", "CN");
        addPdfDay(entries, allowedSections, excludedDays, "A5", "Friday", "DAA", "DAA", "LIBRARY", "LIBRARY", "MO", "MO", "PDNC", "PDNC");
        addPdfDay(entries, allowedSections, excludedDays, "A5", "Saturday", "FCSHPC", "FCSHPC", "PDNC (P)", "PDNC (P)", "CN (P)", "CN (P)", "FSAD (S)", "FSAD (S)");

        addPdfDay(entries, allowedSections, excludedDays, "A6", "Monday", "CN", "CN", "PDNC", "DAA", "FCSHPC", "FCSHPC", "LIBRARY", "LIBRARY");
        addPdfDay(entries, allowedSections, excludedDays, "A6", "Tuesday", "DAA", "DAA", "GLOBAL CERTIFICATION", "GLOBAL CERTIFICATION", "FSAD", "CN", "DAA (P)", "DAA (P)");
        addPdfDay(entries, allowedSections, excludedDays, "A6", "Wednesday", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT", "CRT");
        addPdfDay(entries, allowedSections, excludedDays, "A6", "Thursday", "MO", "MO", "FSAD", "FSAD", "GLOBAL LOGIC", "GLOBAL LOGIC", "DAA (S)", "DAA (S)");
        addPdfDay(entries, allowedSections, excludedDays, "A6", "Friday", "MO", "MO", "PDNC (P)", "PDNC (P)", "FSAD (S)", "FSAD (S)", "FCSHPC", "FCSHPC");
        addPdfDay(entries, allowedSections, excludedDays, "A6", "Saturday", "CN (P)", "CN (P)", "SPORTS", "SPORTS", "PDNC", "PDNC", "FSAD (S)", "FSAD (S)");

        return entries;
    }

    private void addPdfDay(
            List<TimetableEntryInput> entries,
            Set<String> allowedSections,
            Set<String> excludedDays,
            String section,
            String day,
            String... subjects) {
        if (!allowedSections.contains(section.toUpperCase(Locale.ROOT)) || excludedDays.contains(normalizeDay(day))) {
            return;
        }
        Map<String, String> facultyBySubject = PDF_FACULTY_BY_SECTION.getOrDefault(section, Collections.emptyMap());
        for (int index = 0; index < subjects.length; index++) {
            String subject = normalizeSubject(subjects[index]);
            entries.add(new TimetableEntryInput(
                    SECOND_YEAR,
                    section,
                    day,
                    TIME_SLOTS.get(index),
                    subject,
                    facultyBySubject.getOrDefault(subjectKey(subject), ""),
                    inferAllocationType(subject),
                    "II Year default"));
        }
    }

    private List<TimetableEntryInput> parseCustomEntries(Map<String, Object> request) {
        if (request == null) {
            return Collections.emptyList();
        }
        Object rawEntries = request.get("entries");
        if (!(rawEntries instanceof Collection<?> rawCollection)) {
            return Collections.emptyList();
        }

        List<TimetableEntryInput> entries = new ArrayList<>();
        for (Object rawEntry : rawCollection) {
            if (!(rawEntry instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String yearGroup = stringValue(rawMap.get("yearGroup"));
            String section = stringValue(rawMap.get("section")).toUpperCase(Locale.ROOT);
            String day = dayLabel(stringValue(rawMap.get("day")));
            String time = normalizeTime(stringValue(rawMap.get("time")));
            String subject = normalizeSubject(stringValue(rawMap.get("subject")));
            String facultyName = stringValue(rawMap.get("facultyName"));
            String allocationType = normalizeAllocationType(stringValue(rawMap.get("allocationType")), subject);
            if (yearGroup.isBlank() || section.isBlank() || day.isBlank() || time.isBlank() || subject.isBlank()) {
                continue;
            }

            if (NO_ROOM_ACTIVITY_SUBJECT_KEYS.contains(subjectKey(subject))) {
                facultyName = "";
            } else if (normalizeYearGroup(yearGroup).equals(normalizeYearGroup(SECOND_YEAR)) && facultyName.isBlank()) {
                Map<String, String> facultyBySubject = PDF_FACULTY_BY_SECTION.getOrDefault(section, Collections.emptyMap());
                facultyName = facultyBySubject.getOrDefault(subjectKey(subject), facultyName);
            }

            entries.add(new TimetableEntryInput(
                    yearGroup,
                    section,
                    day,
                    time,
                    subject,
                    facultyName,
                    allocationType,
                    "Custom upload"));
        }
        return entries;
    }

    private Map<String, YearConfigInput> parseYearConfigs(Map<String, Object> request) {
        Map<String, YearConfigInput> configs = new LinkedHashMap<>();
        configs.put(normalizeYearGroup(SECOND_YEAR),
                new YearConfigInput(SECOND_YEAR, new HashSet<>(DEFAULT_SECOND_YEAR_SECTIONS), new HashSet<>(DEFAULT_SECOND_YEAR_EXCLUDED_DAYS)));

        if (request == null) {
            return configs;
        }
        Object rawConfigs = request.get("yearConfigs");
        if (!(rawConfigs instanceof Collection<?> rawCollection)) {
            return configs;
        }

        for (Object rawConfig : rawCollection) {
            if (!(rawConfig instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String name = stringValue(rawMap.get("name"));
            if (name.isBlank()) {
                continue;
            }
            Set<String> sections = stringSet(rawMap.get("sections"));
            Set<String> excludedDays = stringSet(rawMap.get("excludedDays")).stream()
                    .map(this::normalizeDay)
                    .filter(day -> !day.isBlank())
                    .collect(Collectors.toCollection(HashSet::new));
            configs.put(normalizeYearGroup(name), new YearConfigInput(name, sections, excludedDays));
        }

        return configs;
    }

    private List<TimetableEntryInput> applyYearConfigRules(List<TimetableEntryInput> entries, Map<String, YearConfigInput> yearConfigs) {
        return entries.stream()
                .filter(entry -> {
                    YearConfigInput config = yearConfigs.get(normalizeYearGroup(entry.yearGroup));
                    if (config == null) {
                        return true;
                    }
                    if (!config.sections.isEmpty() && !config.sections.contains(entry.section.toUpperCase(Locale.ROOT))) {
                        return false;
                    }
                    return !config.excludedDays.contains(normalizeDay(entry.day));
                })
                .toList();
    }

    private void validateDuplicateEntries(List<TimetableEntryInput> entries) {
        Map<String, Long> slotCounts = entries.stream()
                .collect(Collectors.groupingBy(
                        entry -> sectionKey(entry.yearGroup, entry.section) + "|" + normalizeDay(entry.day) + "|" + normalizeTime(entry.time),
                        Collectors.counting()));

        slotCounts.entrySet().stream()
                .filter(item -> item.getValue() > 1)
                .findFirst()
                .ifPresent(item -> {
                    String[] parts = item.getKey().split("\\|", 4);
                    String section = parts.length > 1 ? parts[1] : "UNKNOWN";
                    String day = parts.length > 2 ? dayLabel(parts[2]) : "Unknown day";
                    String time = parts.length > 3 ? parts[3] : "Unknown time";
                    throw new IllegalArgumentException("Duplicate timetable rows for " + section + " at " + day + " " + time);
                });
    }

    private void validateFacultyCapacity(List<TimetableEntryInput> entries, Map<String, YearConfigInput> yearConfigs) {
        Map<String, Integer> activeSlotsByYear = new HashMap<>();
        for (Map.Entry<String, YearConfigInput> configEntry : yearConfigs.entrySet()) {
            int activeDays = getActiveDayIndexes(configEntry.getValue().excludedDays).size();
            activeSlotsByYear.put(configEntry.getKey(), activeDays * TIME_SLOTS.size());
        }

        Map<String, Long> facultyCounts = entries.stream()
                .filter(entry -> !isActivityEntry(entry))
                .map(entry -> new Object[]{normalizeYearGroup(entry.yearGroup), stringValue(entry.facultyName).toLowerCase(Locale.ROOT), stringValue(entry.facultyName)})
                .filter(parts -> !((String) parts[1]).isBlank())
                .collect(Collectors.groupingBy(parts -> parts[0] + "|" + parts[1], Collectors.counting()));

        for (Map.Entry<String, Long> countEntry : facultyCounts.entrySet()) {
            String[] parts = countEntry.getKey().split("\\|", 2);
            String yearKey = parts[0];
            int maxSlots = activeSlotsByYear.getOrDefault(yearKey, TIME_SLOTS.size() * DAYS.size());
            if (countEntry.getValue() > maxSlots) {
                throw new IllegalArgumentException("Faculty load exceeds feasible weekly slots: " + parts[1] + " has " + countEntry.getValue() + " slots (max " + maxSlots + ")");
            }
        }
    }

    private void validateConcurrentRoomCapacity(List<TimetableEntryInput> entries) {
        long labCapacity = LAB_ROOM_CODES.size();
        long classCapacity = CLASSROOM_ROOM_CODES.size();

        Map<String, Long> labDemand = entries.stream()
                .filter(entry -> !isActivityEntry(entry) && requiresLab(entry))
                .collect(Collectors.groupingBy(entry -> normalizeDay(entry.day) + "|" + normalizeTime(entry.time), Collectors.counting()));

        labDemand.entrySet().stream()
                .filter(item -> item.getValue() > labCapacity)
                .findFirst()
                .ifPresent(item -> {
                    String[] parts = item.getKey().split("\\|", 2);
                    throw new IllegalArgumentException("Lab room demand exceeds capacity at " + dayLabel(parts[0]) + " " + parts[1] + ": demand " + item.getValue() + ", available " + labCapacity);
                });

        Map<String, Long> classDemand = entries.stream()
                .filter(entry -> !isActivityEntry(entry) && !requiresLab(entry))
                .collect(Collectors.groupingBy(entry -> normalizeDay(entry.day) + "|" + normalizeTime(entry.time), Collectors.counting()));

        classDemand.entrySet().stream()
                .filter(item -> item.getValue() > classCapacity)
                .findFirst()
                .ifPresent(item -> {
                    String[] parts = item.getKey().split("\\|", 2);
                    throw new IllegalArgumentException("Classroom demand exceeds capacity at " + dayLabel(parts[0]) + " " + parts[1] + ": demand " + item.getValue() + ", available " + classCapacity);
                });
    }

    private void ensureResourcesExist() {
        Set<String> allowedCodes = new HashSet<>(LAB_ROOM_CODES);
        allowedCodes.addAll(CLASSROOM_ROOM_CODES);

        for (String roomCode : LAB_ROOM_CODES) {
            createOrUpdateResource(roomCode, roomCode + " Lab", ResourceType.LAB, 60, "H1 Block");
        }
        for (String roomCode : CLASSROOM_ROOM_CODES) {
            createOrUpdateResource(roomCode, roomCode + " Classroom", ResourceType.CLASSROOM, 60, "H1 Block");
        }

        for (Resource resource : resourceRepository.findAll()) {
            boolean shouldBeActive = allowedCodes.contains(resource.getResourceCode());
            if (resource.isActive() != shouldBeActive) {
                resource.setActive(shouldBeActive);
                resourceRepository.save(resource);
            }
        }
    }

    private void createOrUpdateResource(String code, String name, ResourceType type, int capacity, String location) {
        Resource resource = resourceRepository.findByResourceCode(code).orElseGet(Resource::new);
        resource.setResourceCode(code);
        resource.setResourceName(name);
        resource.setResourceType(type);
        resource.setCapacity(capacity);
        resource.setLocation(location);
        resource.setActive(true);
        resourceRepository.save(resource);
    }

    private List<Resource> filterAllowedRooms(List<Resource> allResources, List<String> allowedCodes) {
        return allResources.stream()
                .filter(Resource::isActive)
                .filter(resource -> allowedCodes.contains(resource.getResourceCode()))
                .sorted(Comparator.comparingInt(resource -> allowedCodes.indexOf(resource.getResourceCode())))
                .toList();
    }

    private List<Allocation> generateFromCustomEntries(
            List<TimetableEntryInput> entries,
            Map<String, YearConfigInput> yearConfigs,
            List<Resource> classroomRooms,
            List<Resource> labRooms) {

        validateCustomTotals(entries, yearConfigs);
        validateCustomPracticalPairs(entries);

        List<Allocation> allocations = new ArrayList<>();
        Map<String, Set<Long>> roomUsage = new HashMap<>();

        List<TimetableEntryInput> ordered = entries.stream()
                .sorted(Comparator
                        .comparingInt((TimetableEntryInput entry) -> DAY_ORDER.getOrDefault(normalizeDay(entry.day), Integer.MAX_VALUE))
                        .thenComparingInt(entry -> TIME_SLOTS.indexOf(entry.time))
                        .thenComparingInt(this::allocationPriority)
                        .thenComparing(entry -> entry.section))
                .toList();

        for (TimetableEntryInput entry : ordered) {
            String usageKey = normalizeDay(entry.day) + "|" + entry.time;

            Resource assignedRoom = null;
            if (!isActivityEntry(entry)) {
                List<Resource> pool = requiresLab(entry) ? labRooms : classroomRooms;
                Set<Long> usedRooms = roomUsage.computeIfAbsent(usageKey, key -> new HashSet<>());
                for (Resource room : pool) {
                    if (!usedRooms.contains(room.getId())) {
                        assignedRoom = room;
                        usedRooms.add(room.getId());
                        break;
                    }
                }
            }

            Allocation allocation = new Allocation();
            allocation.setYearGroup(entry.yearGroup);
            allocation.setSection(entry.section);
            allocation.setDay(entry.day);
            allocation.setTime(entry.time);
            allocation.setSubject(entry.subject);
            allocation.setFacultyName(entry.facultyName);
            allocation.setAllocationType(entry.allocationType);
            allocation.setSourceLabel(entry.sourceLabel);
            allocation.setRoom(assignedRoom);
            allocations.add(allocation);
        }

        return allocations;
    }

    private void validateCustomTotals(List<TimetableEntryInput> entries, Map<String, YearConfigInput> yearConfigs) {
        Map<String, Long> sectionCounts = entries.stream()
                .collect(Collectors.groupingBy(entry -> sectionKey(entry.yearGroup, entry.section), Collectors.counting()));

        for (Map.Entry<String, Long> countEntry : sectionCounts.entrySet()) {
            String[] parts = countEntry.getKey().split("\\|", 2);
            String yearGroup = parts[0];
            String section = parts[1];
            YearConfigInput config = yearConfigs.getOrDefault(yearGroup, new YearConfigInput(yearGroup, Set.of(), Set.of()));
            int expectedSlots = getActiveDayIndexes(config.excludedDays).size() * TIME_SLOTS.size();
            if (countEntry.getValue().intValue() != expectedSlots) {
                throw new IllegalArgumentException("Invalid LTPS totals for " + section + ": expected " + expectedSlots + " slots, found " + countEntry.getValue());
            }
        }
    }

    private void validateCustomPracticalPairs(List<TimetableEntryInput> entries) {
        Map<String, List<TimetableEntryInput>> grouped = entries.stream()
                .collect(Collectors.groupingBy(entry -> sectionKey(entry.yearGroup, entry.section) + "|" + normalizeDay(entry.day)));

        for (List<TimetableEntryInput> dayEntries : grouped.values()) {
            List<TimetableEntryInput> ordered = dayEntries.stream()
                    .sorted(Comparator.comparingInt(entry -> TIME_SLOTS.indexOf(entry.time)))
                    .toList();

            int index = 0;
            while (index < ordered.size()) {
                TimetableEntryInput entry = ordered.get(index);
                if (!requiresPair(entry)) {
                    index++;
                    continue;
                }

                int run = 1;
                int startSlot = TIME_SLOTS.indexOf(entry.time);
                while (index + run < ordered.size()) {
                    TimetableEntryInput next = ordered.get(index + run);
                    if (requiresPair(next)
                            && subjectKey(next.subject).equals(subjectKey(entry.subject))
                            && TIME_SLOTS.indexOf(next.time) == startSlot + run) {
                        run++;
                    } else {
                        break;
                    }
                }

                if (run != 2) {
                    throw new IllegalArgumentException("Invalid LTPS count for " + entry.subject + " in " + entry.section + ": LAB/SKILL requires consecutive pair");
                }
                index += run;
            }
        }
    }

    private int allocationPriority(TimetableEntryInput entry) {
        if (requiresPair(entry)) {
            return 1;
        }
        if (isActivityEntry(entry)) {
            return 3;
        }
        return 2;
    }

    private boolean requiresPair(TimetableEntryInput entry) {
        String type = entry.allocationType == null ? "" : entry.allocationType.trim().toUpperCase(Locale.ROOT);
        String subject = entry.subject == null ? "" : entry.subject.toUpperCase(Locale.ROOT);
        return "LAB".equals(type) || "SKILL".equals(type) || subject.contains("(P)") || subject.contains("(S)");
    }

    private boolean requiresLab(TimetableEntryInput entry) {
        String type = entry.allocationType == null ? "" : entry.allocationType.trim().toUpperCase(Locale.ROOT);
        String subject = entry.subject == null ? "" : entry.subject.toUpperCase(Locale.ROOT);
        return "LAB".equals(type) || subject.contains("(P)");
    }

    private boolean isActivityEntry(TimetableEntryInput entry) {
        String type = entry.allocationType == null ? "" : entry.allocationType.trim().toUpperCase(Locale.ROOT);
        return "ACTIVITY".equals(type) || NO_ROOM_ACTIVITY_SUBJECT_KEYS.contains(subjectKey(entry.subject));
    }

    private boolean requiresManagedRoom(TimetableEntryInput entry) {
        return !NO_ROOM_ACTIVITY_SUBJECT_KEYS.contains(subjectKey(entry.subject));
    }

    private int slotOrder(String key) {
        String[] parts = key.split("\\|", 2);
        int dayIndex = DAY_ORDER.getOrDefault(parts[0], Integer.MAX_VALUE);
        int timeIndex = TIME_SLOTS.indexOf(parts[1]);
        return (dayIndex * 100) + (timeIndex < 0 ? 99 : timeIndex);
    }

    private String normalizeAllocationType(String type, String subject) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if ("PRACTICAL".equals(normalized)) {
            return "LAB";
        }
        if ("SEMINAR".equals(normalized)) {
            return "LECTURE";
        }
        if (!normalized.isBlank()) {
            return normalized;
        }
        return inferAllocationType(subject);
    }

    private String inferAllocationType(String subject) {
        if (NO_ROOM_ACTIVITY_SUBJECT_KEYS.contains(subjectKey(subject))) {
            return "ACTIVITY";
        }
        if (subject.contains("(P)")) {
            return "LAB";
        }
        if (subject.contains("(S)")) {
            return "SKILL";
        }
        if (subject.contains("(T)")) {
            return "TUTORIAL";
        }
        return "LECTURE";
    }

    private String normalizeSubject(String subject) {
        return subject == null ? "" : subject.replaceAll("\\s+", " ").trim();
    }

    private String subjectKey(String subject) {
        String normalized = normalizeSubject(subject)
                .replace("II-", "")
                .replace("(L)", "")
                .replace("(P)", "")
                .replace("(S)", "")
                .trim();
        if ("FULL STACK APPLICATION DEVELOPMENT".equalsIgnoreCase(normalized) || "PYTHON FULL STACK DEVELOPMENT".equalsIgnoreCase(normalized)) {
            return "FSAD";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeTime(String time) {
        String normalized = time == null ? "" : time.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return normalized;
        }
        return TIME_SLOTS.stream()
                .filter(slot -> slot.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(normalized);
    }

    private String normalizeDay(String day) {
        return day == null ? "" : day.trim().toUpperCase(Locale.ROOT);
    }

    private String dayLabel(String day) {
        return DAY_LABELS.getOrDefault(normalizeDay(day), day == null ? "" : day.trim());
    }

    private String normalizeYearGroup(String yearGroup) {
        return yearGroup == null ? "" : yearGroup.trim().toUpperCase(Locale.ROOT);
    }

    private Set<String> stringSet(Object rawValue) {
        if (rawValue instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::stringValue)
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(HashSet::new));
        }
        if (rawValue instanceof String stringValue) {
            Set<String> values = new HashSet<>();
            for (String value : stringValue.split(",")) {
                String normalized = value.trim().toUpperCase(Locale.ROOT);
                if (!normalized.isBlank()) {
                    values.add(normalized);
                }
            }
            return values;
        }
        return new HashSet<>();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Map<String, Map<String, String>> buildPdfFacultyBySection() {
        Map<String, Map<String, String>> facultyBySection = new HashMap<>();
        facultyBySection.put("E1", faculty(
                "DAA", "Dr. Anitha Patil",
                "MO", "Dr Kalyan Kumar P",
                "PFSD", "Mr Chanda Raj Kumar",
                "FSAD", "Ms P Sree Lakshmi & Dr Bhaskar Reddy",
                "CN", "Dr Trinath Basu",
                "CTI", "Dr Lalitha",
                "GLOBAL LOGIC", "Ms. Saritha"));
        facultyBySection.put("E2", faculty(
                "DAA", "Dr. Sumalakshmi",
                "MO", "Mr Jaganmohan Rao",
                "PFSD", "Mr Chanda Raj Kumar",
                "FSAD", "Dr N Ravinder & Dr. Ratna Kumar",
                "CN", "Dr Pavan Kumar",
                "NLP", "Dr. Bhaskar Reddy",
                "GLOBAL LOGIC", "Ms. Sreelakshmi"));
        facultyBySection.put("E3", faculty(
                "DAA", "Dr. Sumalakshmi",
                "MO", "Dr Suhma Rani",
                "PFSD", "Mr Chanda Raj Kumar",
                "FSAD", "Dr. Ratna Kumar & Dr N Ravinder",
                "CN", "Mr Narasimha",
                "NLP", "Dr. Bhaskar Reddy",
                "GLOBAL LOGIC", "Dr. Ravinder"));
        facultyBySection.put("A1", faculty(
                "DAA", "Dr. Saidireddy M",
                "MO", "Dr Suhma Rani",
                "FSAD", "Ms P Sree Lakshmi & Dr Anuradha",
                "CN", "Mr Narasimha / Dr Pavan Kumar",
                "CTI", "Dr Lalitha",
                "PDNC", "Dr. A Mousmi Chaurasia / Dr. Siva Krishna Reddy",
                "GLOBAL LOGIC", "Dr. Sasidhar K"));
        facultyBySection.put("A2", faculty(
                "DAA", "Dr. Saidireddy M",
                "MO", "Mr Jaganmohan Rao",
                "FSAD", "Dr Anuradha & Dr. Bhaskar Reddy",
                "CN", "Dr Pavan Kumar",
                "PDNC", "Dr. A Mousmi Chaurasia",
                "FCSHPC", "Ms. PNS Soumya",
                "GLOBAL LOGIC", "Dr. Rafeeq"));
        facultyBySection.put("A3", faculty(
                "DAA", "Mr Aftab Yaseen",
                "MO", "Dr Suhma Rani",
                "FSAD", "Dr Anuradha & Dr. Anantha Reddy",
                "CN", "Mr Narasimha",
                "PDNC", "Dr. A Mousmi Chaurasia",
                "FCSHPC", "Ms. PNS Soumya",
                "GLOBAL LOGIC", "Dr. Bhaskar Reddy"));
        facultyBySection.put("A4", faculty(
                "DAA", "Mr Aftab Yaseen",
                "MO", "Mr Jaganmohan Rao",
                "FSAD", "Dr Madhukar & Chiranjeevi",
                "CN", "Dr Pavan Kumar",
                "PDNC", "Dr. Siva Krishna Reddy",
                "FCSHPC", "Ms. PNS Soumya",
                "GLOBAL LOGIC", "Dr. Hari Prasad"));
        facultyBySection.put("A5", faculty(
                "DAA", "Dr. Sasidhar K & Dr. Anitha Patil",
                "MO", "Mr Jaganmohan Rao",
                "FSAD", "Dr. Ratnakumar & Dr N Ravinder",
                "CN", "Mr Narasimha",
                "PDNC", "Dr. Siva Krishna Reddy",
                "FCSHPC", "Ms. PNS Soumya",
                "GLOBAL LOGIC", "Ms. Soumya Bharadwaj"));
        facultyBySection.put("A6", faculty(
                "DAA", "Mr Aftab Yaseen",
                "MO", "Dr Suhma Rani",
                "FSAD", "Mr. Chiranjeevi & Madhukar",
                "CN", "Dr Trinath Basu",
                "PDNC", "Dr. Siva Krishna Reddy",
                "FCSHPC", "Ms. Mubeena Bagum",
                "GLOBAL LOGIC", "Mr. Chiranjeevi N"));
        return facultyBySection;
    }

    private static Map<String, String> faculty(String... values) {
        Map<String, String> facultyMap = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            facultyMap.put(values[index].toUpperCase(Locale.ROOT), values[index + 1]);
        }
        return facultyMap;
    }

    private enum LtpsType {
        ACTIVITY,
        LECTURE,
        TUTORIAL,
        PRACTICAL
    }

    private static class SchedulerState {

        private final List<Resource> classRooms;
        private final List<Resource> labRooms;
        private final Random random;
        private final Map<String, Set<String>> facultyUsage = new HashMap<>();
        private final Map<String, Set<Long>> roomUsage = new HashMap<>();
        private final Map<String, SectionSchedule> sectionSchedules = new LinkedHashMap<>();
        private final Map<String, SectionDemand> sectionDemands = new HashMap<>();

        private SchedulerState(List<Resource> classRooms, List<Resource> labRooms, int seed) {
            this.classRooms = classRooms;
            this.labRooms = labRooms;
            this.random = new Random(System.nanoTime() + seed);
        }
    }

    private static class SectionSchedule {

        private final String yearGroup;
        private final String section;
        private final SlotAssignment[][] grid = new SlotAssignment[DAYS.size()][TIME_SLOTS.size()];

        private SectionSchedule(String yearGroup, String section) {
            this.yearGroup = yearGroup;
            this.section = section;
        }
    }

    private static class SlotAssignment {

        private final String subjectLabel;
        private final String subjectKey;
        private final String facultyName;
        private final String allocationType;
        private final LtpsType ltpsType;
        private final Resource room;
        private final String sourceLabel;

        private SlotAssignment(String subjectLabel, String subjectKey, String facultyName, String allocationType, LtpsType ltpsType, Resource room, String sourceLabel) {
            this.subjectLabel = subjectLabel;
            this.subjectKey = subjectKey;
            this.facultyName = facultyName;
            this.allocationType = allocationType;
            this.ltpsType = ltpsType;
            this.room = room;
            this.sourceLabel = sourceLabel;
        }
    }

    private static class SectionDemand {

        private final String yearGroup;
        private final String section;
        private final List<DemandUnit> practicalUnits;
        private final List<DemandUnit> lectureUnits;
        private final List<DemandUnit> tutorialUnits;
        private final Map<String, Integer> requiredCounts;
        private final List<Integer> activeDayIndexes;

        private SectionDemand(
                String yearGroup,
                String section,
                List<DemandUnit> practicalUnits,
                List<DemandUnit> lectureUnits,
                List<DemandUnit> tutorialUnits,
                Map<String, Integer> requiredCounts,
                List<Integer> activeDayIndexes) {
            this.yearGroup = yearGroup;
            this.section = section;
            this.practicalUnits = practicalUnits;
            this.lectureUnits = lectureUnits;
            this.tutorialUnits = tutorialUnits;
            this.requiredCounts = requiredCounts;
            this.activeDayIndexes = activeDayIndexes;
        }
    }

    private static class DemandAccumulator {

        private final String subjectKey;
        private final String subjectLabel;
        private String facultyName;
        private final String allocationType;
        private final LtpsType ltpsType;
        private int slots;

        private DemandAccumulator(String subjectKey, String subjectLabel, String facultyName, String allocationType, LtpsType ltpsType) {
            this.subjectKey = subjectKey;
            this.subjectLabel = subjectLabel;
            this.facultyName = facultyName == null ? "" : facultyName;
            this.allocationType = allocationType;
            this.ltpsType = ltpsType;
            this.slots = 0;
        }
    }

    private static class DemandUnit {

        private final String subjectKey;
        private final String subjectLabel;
        private final String facultyName;
        private final String allocationType;
        private final LtpsType ltpsType;
        private int remainingSlots;

        private DemandUnit(String subjectKey, String subjectLabel, String facultyName, String allocationType, LtpsType ltpsType, int remainingSlots) {
            this.subjectKey = subjectKey;
            this.subjectLabel = subjectLabel;
            this.facultyName = facultyName == null ? "" : facultyName;
            this.allocationType = allocationType;
            this.ltpsType = ltpsType;
            this.remainingSlots = remainingSlots;
        }
    }

    private static class CandidatePlacement {

        private final int day;
        private final int slot;
        private final Resource room;
        private final int subjectDayCount;
        private final int dayFillCount;
        private final int dayUniqueSubjects;

        private CandidatePlacement(int day, int slot, Resource room, int subjectDayCount, int dayFillCount, int dayUniqueSubjects) {
            this.day = day;
            this.slot = slot;
            this.room = room;
            this.subjectDayCount = subjectDayCount;
            this.dayFillCount = dayFillCount;
            this.dayUniqueSubjects = dayUniqueSubjects;
        }
    }

    private static class TimetableEntryInput {

        private final String yearGroup;
        private final String section;
        private final String day;
        private final String time;
        private final String subject;
        private final String facultyName;
        private final String allocationType;
        private final String sourceLabel;

        private TimetableEntryInput(
                String yearGroup,
                String section,
                String day,
                String time,
                String subject,
                String facultyName,
                String allocationType,
                String sourceLabel) {
            this.yearGroup = yearGroup;
            this.section = section;
            this.day = day;
            this.time = time;
            this.subject = subject;
            this.facultyName = facultyName;
            this.allocationType = allocationType;
            this.sourceLabel = sourceLabel;
        }
    }

    private static class YearConfigInput {

        private final String name;
        private final Set<String> sections;
        private final Set<String> excludedDays;

        private YearConfigInput(String name, Set<String> sections, Set<String> excludedDays) {
            this.name = name;
            this.sections = sections;
            this.excludedDays = excludedDays;
        }
    }
}
