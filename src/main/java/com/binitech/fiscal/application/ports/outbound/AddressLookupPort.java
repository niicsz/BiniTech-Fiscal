package com.binitech.fiscal.application.ports.outbound;

import com.binitech.fiscal.domain.Address;
import java.util.Optional;

public interface AddressLookupPort {
  Optional<Address> findByCep(String cep);
}
