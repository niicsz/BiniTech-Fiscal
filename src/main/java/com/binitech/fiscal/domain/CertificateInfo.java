package com.binitech.fiscal.domain;

import java.time.LocalDate;

public record CertificateInfo(String holder, String document, LocalDate validUntil) {}
