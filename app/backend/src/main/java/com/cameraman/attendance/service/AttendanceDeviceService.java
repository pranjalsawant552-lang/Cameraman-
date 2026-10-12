package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.AttendanceDevice;
import com.cameraman.attendance.repository.AttendanceDeviceRepository;
import com.cameraman.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Database-backed list of attendance machines. This is a separate bean so that recording a
 * first-time device uses its own transaction: the unknown-device request is rejected (which rolls
 * back the caller), but the admin must still see the serial in the list afterwards.
 */
@Service
@RequiredArgsConstructor
public class AttendanceDeviceService {

    private final AttendanceDeviceRepository repository;

    /** Records that a device called. New devices start as NOT allowed. Returns whether it is allowed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean noteSeen(String serial) {
        String key = serial.trim();
        AttendanceDevice device = repository.findById(key).orElse(null);
        if (device == null) {
            repository.save(AttendanceDevice.builder().serial(key).allowed(false).build());
            return false;
        }
        device.setLastSeenAt(LocalDateTime.now());
        repository.save(device);
        return device.isAllowed();
    }

    @Transactional
    public void addPunches(String serial, long count) {
        if (serial == null || serial.isBlank() || count <= 0) return;
        repository.addPunches(serial.trim(), count, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<AttendanceDevice> list() {
        return repository.findAllByOrderByLastSeenAtDesc();
    }

    @Transactional
    public AttendanceDevice update(String serial, Boolean allowed, String label) {
        AttendanceDevice d = repository.findById(serial)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "serial", serial));
        if (allowed != null) d.setAllowed(allowed);
        if (label != null) d.setLabel(label.trim().isEmpty() ? null : label.trim());
        return repository.save(d);
    }
}
