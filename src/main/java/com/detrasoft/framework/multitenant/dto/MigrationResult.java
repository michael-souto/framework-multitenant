package com.detrasoft.framework.multitenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MigrationResult {
    private String schema;
    private boolean success;
    private int migrationsExecuted;
    private String errorMessage;
    private long durationMs;
}
