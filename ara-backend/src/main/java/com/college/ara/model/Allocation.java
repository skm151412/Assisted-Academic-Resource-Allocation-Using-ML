package com.college.ara.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "allocation")
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id")
    private Long id;

    @Column(name = "section_name", nullable = false, length = 20)
    private String section;

    @Column(name = "year_group", length = 40)
    private String yearGroup;

    @Column(name = "day_of_week", nullable = false, length = 20)
    private String day;

    @Column(name = "time_slot", nullable = false, length = 50)
    private String time;

    @Column(name = "subject", nullable = false, length = 100)
    private String subject;

    @Column(name = "faculty_name", length = 255)
    private String facultyName;

    @Column(name = "allocation_type", length = 30)
    private String allocationType;

    @Column(name = "source_label", length = 60)
    private String sourceLabel;

    @ManyToOne(optional = true)
    @JoinColumn(name = "resource_id")
    private Resource room;

    public Allocation() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getYearGroup() {
        return yearGroup;
    }

    public void setYearGroup(String yearGroup) {
        this.yearGroup = yearGroup;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getFacultyName() {
        return facultyName;
    }

    public void setFacultyName(String facultyName) {
        this.facultyName = facultyName;
    }

    public String getAllocationType() {
        return allocationType;
    }

    public void setAllocationType(String allocationType) {
        this.allocationType = allocationType;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public Resource getRoom() {
        return room;
    }

    public void setRoom(Resource room) {
        this.room = room;
    }
}
