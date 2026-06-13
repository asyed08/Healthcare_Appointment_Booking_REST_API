package com.ameen.healthcare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA Auditing so that {@code @CreatedDate} and {@code @LastModifiedDate}
 * annotations on entities are automatically populated on persist/merge.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}

