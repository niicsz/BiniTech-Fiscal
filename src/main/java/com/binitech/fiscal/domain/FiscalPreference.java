package com.binitech.fiscal.domain;

import java.time.Instant;

public record FiscalPreference(
    String tenantId, boolean nfceEnabled, Instant decidedAt, String decidedBy) {}
