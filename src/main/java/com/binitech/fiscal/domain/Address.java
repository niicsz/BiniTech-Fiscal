package com.binitech.fiscal.domain;

public record Address(
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String codigoMunicipio,
    String cidade,
    String uf,
    String cep) {}
