package com.college.ara.config;

import java.util.List;
import java.util.Set;

public final class RoomCatalog {

    public static final List<String> LAB_ROOM_CODES = List.of("H1-01", "H1-02", "H1-03", "H1-04");

    public static final List<String> CLASSROOM_ROOM_CODES = List.of(
            "H1-17", "H1-18", "H1-19", "H1-22", "H1-23", "H1-25", "H1-26");

    public static final List<String> MANAGED_ROOM_CODES = List.of(
            "H1-01", "H1-02", "H1-03", "H1-04",
            "H1-17", "H1-18", "H1-19", "H1-22", "H1-23", "H1-25", "H1-26");

    public static final Set<String> MANAGED_ROOM_CODE_SET = Set.copyOf(MANAGED_ROOM_CODES);

    private RoomCatalog() {
    }
}
