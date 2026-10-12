package com.cameraman.audit.service;

import com.cameraman.audit.domain.AuditLog;
import com.cameraman.audit.dto.AuditDto;
import com.cameraman.audit.repository.AuditLogRepository;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, String entityId,
                    String oldValue, String newValue, String performedBy) {
        try {
            String user = performedBy;
            if (user == null) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    user = auth.getName();
                }
            }

            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .performedBy(user)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logWithModule(String action, String entityType, String entityId,
                               String oldValue, String newValue, String performedBy, String module) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action).entityType(entityType).entityId(entityId)
                    .oldValue(oldValue).newValue(newValue)
                    .performedBy(performedBy).module(module)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    /** The read side this service was always missing — AuditLog, its repository, and this
     *  service's write methods all existed already, but nothing ever exposed them over
     *  HTTP, so the Audit Logs page had no endpoint to call at all ("unable to fetch",
     *  every time, unconditionally — not a bug in any request, there was simply nothing
     *  there to answer it). See AuditController. */
    @Transactional(readOnly = true)
    public PagedResponse<AuditDto.LogResponse> search(String term, Pageable pageable) {
        Page<AuditLog> page = (term != null && !term.isBlank())
                ? auditLogRepository.search(term.trim(), pageable)
                : auditLogRepository.findAll(pageable);
        return PagedResponse.of(page.map(this::toResponse));
    }

    private AuditDto.LogResponse toResponse(AuditLog a) {
        String performedByName = null;
        if (a.getPerformedBy() != null) {
            var user = userRepository.findByEmail(a.getPerformedBy()).orElse(null);
            if (user != null) {
                performedByName = (user.getFirstName() + " " + user.getLastName()).trim();
            }
        }
        return AuditDto.LogResponse.builder()
                .id(a.getId())
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .performedBy(a.getPerformedBy())
                .performedByName(performedByName)
                .details(a.getDescription())
                .ipAddress(a.getIpAddress())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
