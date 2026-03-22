package com.securebank.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base entity that all JPA entities extend.
 * Provides: UUID primary key, optimistic locking, audit timestamps.
 *
 * Why UUID over auto-increment?
 * - No sequential guessing of IDs (security)
 * - Safe for distributed systems (no DB coordination needed)
 * - Canadian banks use UUIDs for account/transaction references
 *
 * Why @Version?
 * - Optimistic locking prevents lost updates when two users edit the same record.
 *   JPA automatically checks the version on UPDATE and throws OptimisticLockException if stale.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Version
    private Long version;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
