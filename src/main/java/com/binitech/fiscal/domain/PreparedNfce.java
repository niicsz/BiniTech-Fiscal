package com.binitech.fiscal.domain;

public record PreparedNfce(String chave, String signedXml, String qrCode, String urlChave) {}
