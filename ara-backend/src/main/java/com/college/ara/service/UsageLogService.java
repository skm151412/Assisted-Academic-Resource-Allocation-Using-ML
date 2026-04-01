package com.college.ara.service;

import com.college.ara.model.Booking;
import com.college.ara.model.Resource;
import com.college.ara.model.UsageLog;
import com.college.ara.model.UsageState;
import com.college.ara.repository.UsageLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageLogService {

    private final UsageLogRepository usageLogRepository;

    public UsageLogService(UsageLogRepository usageLogRepository) {
        this.usageLogRepository = usageLogRepository;
    }

    @Transactional
    public UsageLog recordUsage(Resource resource, Booking booking, UsageState usageState, String notes) {
        if (resource == null || usageState == null) {
            throw new IllegalArgumentException("Resource and usage state are required");
        }

        UsageLog usageLog = new UsageLog();
        usageLog.setResource(resource);
        usageLog.setBooking(booking);
        usageLog.setUsageState(usageState);
        usageLog.setNotes(notes);
        return usageLogRepository.save(usageLog);
    }

    @Transactional(readOnly = true)
    public List<UsageLog> listUsageLogs() {
        return usageLogRepository.findAll();
    }
}
