package com.binitech.fiscal.adapters.outbound.http;

import com.binitech.fiscal.application.ports.outbound.AddressLookupPort;
import com.binitech.fiscal.domain.Address;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Optional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class ViaCepAddressAdapter implements AddressLookupPort {

  private final RestClient client;

  public ViaCepAddressAdapter(RestClient client) {
    this.client = client;
  }

  @Override
  public Optional<Address> findByCep(String cep) {
    String digits = cep == null ? "" : cep.replaceAll("\\D", "");
    if (digits.length() != 8) {
      return Optional.empty();
    }
    try {
      ViaCepResponse response =
          client.get().uri("/ws/{cep}/json/", digits).retrieve().body(ViaCepResponse.class);
      if (response == null || Boolean.TRUE.equals(response.erro())) {
        return Optional.empty();
      }
      return Optional.of(
          new Address(
              response.logradouro(),
              null,
              null,
              response.bairro(),
              response.ibge(),
              response.localidade(),
              response.uf(),
              digits));
    } catch (RestClientException e) {
      return Optional.empty();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ViaCepResponse(
      String logradouro, String bairro, String localidade, String uf, String ibge, Boolean erro) {}
}
