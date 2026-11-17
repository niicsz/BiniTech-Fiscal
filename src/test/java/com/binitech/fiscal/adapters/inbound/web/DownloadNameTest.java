package com.binitech.fiscal.adapters.inbound.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DownloadNameTest {

  @Test
  void removesHeaderBreakingCharacters() {
    assertEquals("s1Set-Cookiex1", DownloadName.safe("s1\r\nSet-Cookie: x=1\""));
  }

  @Test
  void fallsBackWhenNothingIsLeft() {
    assertEquals("documento", DownloadName.safe("\r\n\""));
    assertEquals("documento", DownloadName.safe(null));
  }
}
