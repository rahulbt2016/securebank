package com.securebank.account.service;

import com.securebank.account.entity.AuditAction;
import com.securebank.account.entity.AuditLog;
import com.securebank.account.repository.AuditLogRepository;
import com.securebank.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Persists an audit entry in a new transaction so that a rollback of the
     * calling transaction does not suppress the audit record.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String entityType, UUID entityId, AuditAction action,
                    AuthenticatedUser caller, String details) {
        AuditLog entry = new AuditLog();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setDetails(details);

        if (caller != null) {
            entry.setPerformedBy(caller.userId());
            entry.setPerformedByEmail(caller.email());
        }

        auditLogRepository.save(entry);
        log.debug("Audit: {} {} by {}", action, entityId, caller != null ? caller.email() : "system");
    }
}
