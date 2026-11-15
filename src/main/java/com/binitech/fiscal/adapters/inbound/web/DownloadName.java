package com.binitech.fiscal.adapters.inbound.web;

final class DownloadName {

  private DownloadName() {}

  static String safe(String id) {
    if (id == null) {
      return "documento";
    }
    String cleaned = id.replaceAll("[^A-Za-z0-9._-]", "");
    if (cleaned.isEmpty()) {
      return "documento";
    }
    return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
  }
}
