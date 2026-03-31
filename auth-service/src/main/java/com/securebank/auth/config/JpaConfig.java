package com.securebank.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA Auditing so @CreatedDate and @LastModifiedDate
 * on BaseEntity are automatically populated.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
