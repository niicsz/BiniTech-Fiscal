package com.binitech.fiscal.config;

import com.binitech.fiscal.adapters.inbound.messaging.SaleCompletedListener;
import com.binitech.fiscal.adapters.inbound.messaging.SaleReturnedListener;
import com.binitech.fiscal.adapters.inbound.scheduling.NfceRetryScheduler;
import com.binitech.fiscal.adapters.inbound.scheduling.ReturnRetryScheduler;
import com.binitech.fiscal.adapters.outbound.crypto.AesGcmSecretCipher;
import com.binitech.fiscal.adapters.outbound.http.HttpAuthenticationAdapter;
import com.binitech.fiscal.adapters.outbound.http.ViaCepAddressAdapter;
import com.binitech.fiscal.adapters.outbound.persistence.MongoFiscalDocumentRepository;
import com.binitech.fiscal.adapters.outbound.persistence.MongoFiscalPreferenceRepository;
import com.binitech.fiscal.adapters.outbound.persistence.MongoIssuerRepository;
import com.binitech.fiscal.adapters.outbound.persistence.MongoNumberSequence;
import com.binitech.fiscal.adapters.outbound.persistence.MongoReturnDocumentRepository;
import com.binitech.fiscal.adapters.outbound.sefaz.JasperDanfeAdapter;
import com.binitech.fiscal.adapters.outbound.sefaz.JavaNfeSefazAdapter;
import com.binitech.fiscal.adapters.outbound.sefaz.NfceXmlBuilder;
import com.binitech.fiscal.adapters.outbound.sefaz.NfceXmlBuilder.ResponsibleTechnician;
import com.binitech.fiscal.application.ports.inbound.FiscalPreferenceUseCase;
import com.binitech.fiscal.application.ports.inbound.IssuerUseCase;
import com.binitech.fiscal.application.ports.inbound.NfceUseCase;
import com.binitech.fiscal.application.ports.inbound.ReturnUseCase;
import com.binitech.fiscal.application.ports.outbound.AddressLookupPort;
import com.binitech.fiscal.application.ports.outbound.AuthenticationPort;
import com.binitech.fiscal.application.ports.outbound.DanfePort;
import com.binitech.fiscal.application.ports.outbound.FiscalDocumentRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.FiscalPreferenceRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.IssuerRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.NumberSequencePort;
import com.binitech.fiscal.application.ports.outbound.ReturnDocumentRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.SecretCipherPort;
import com.binitech.fiscal.application.ports.outbound.SefazPort;
import com.binitech.fiscal.application.usecases.FiscalPreferenceService;
import com.binitech.fiscal.application.usecases.IssuerService;
import com.binitech.fiscal.application.usecases.NfceProcessor;
import com.binitech.fiscal.application.usecases.NfceService;
import com.binitech.fiscal.application.usecases.ReturnProcessor;
import com.binitech.fiscal.application.usecases.ReturnService;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BeanConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  MongoFiscalDocumentRepository fiscalDocumentRepository(MongoTemplate mongo) {
    MongoFiscalDocumentRepository repository = new MongoFiscalDocumentRepository(mongo);
    repository.ensureIndexes();
    return repository;
  }

  @Bean
  MongoReturnDocumentRepository returnDocumentRepository(MongoTemplate mongo) {
    MongoReturnDocumentRepository repository = new MongoReturnDocumentRepository(mongo);
    repository.ensureIndexes();
    return repository;
  }

  @Bean
  MongoIssuerRepository issuerRepository(MongoTemplate mongo) {
    MongoIssuerRepository repository = new MongoIssuerRepository(mongo);
    repository.ensureIndexes();
    return repository;
  }

  @Bean
  MongoFiscalPreferenceRepository fiscalPreferenceRepository(MongoTemplate mongo) {
    MongoFiscalPreferenceRepository repository = new MongoFiscalPreferenceRepository(mongo);
    repository.ensureIndexes();
    return repository;
  }

  @Bean
  FiscalPreferenceUseCase fiscalPreferenceUseCase(
      FiscalPreferenceRepositoryPort preferences, Clock clock) {
    return new FiscalPreferenceService(preferences, clock);
  }

  @Bean
  NumberSequencePort numberSequence(MongoTemplate mongo) {
    return new MongoNumberSequence(mongo);
  }

  @Bean
  SecretCipherPort secretCipher(@Value("${fiscal.master-key}") String masterKey) {
    return new AesGcmSecretCipher(masterKey);
  }

  @Bean
  SefazPort sefaz(
      @Value("${fiscal.sefaz-timeout-ms:20000}") int timeoutMillis,
      @Value("${fiscal.resp-tec.cnpj:}") String cnpj,
      @Value("${fiscal.resp-tec.contact:}") String contact,
      @Value("${fiscal.resp-tec.email:}") String email,
      @Value("${fiscal.resp-tec.phone:}") String phone) {
    NfceXmlBuilder builder =
        new NfceXmlBuilder(
            new ResponsibleTechnician(
                blankToNull(cnpj), blankToNull(contact), blankToNull(email), blankToNull(phone)));
    return new JavaNfeSefazAdapter(SchemaDirectory.extract(), timeoutMillis, builder);
  }

  @Bean
  DanfePort danfe() {
    return new JasperDanfeAdapter();
  }

  @Bean(destroyMethod = "close")
  ExecutorService fiscalExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean
  NfceProcessor nfceProcessor(
      FiscalDocumentRepositoryPort documents,
      IssuerRepositoryPort issuers,
      NumberSequencePort sequences,
      SefazPort sefaz,
      SecretCipherPort cipher,
      Clock clock) {
    return new NfceProcessor(documents, issuers, sequences, sefaz, cipher, clock);
  }

  @Bean
  ReturnProcessor returnProcessor(
      ReturnDocumentRepositoryPort returns,
      FiscalDocumentRepositoryPort documents,
      IssuerRepositoryPort issuers,
      NumberSequencePort sequences,
      SefazPort sefaz,
      SecretCipherPort cipher,
      Clock clock,
      @Value("${fiscal.nfce.cancel-window-minutes:30}") long cancelWindowMinutes) {
    return new ReturnProcessor(
        returns,
        documents,
        issuers,
        sequences,
        sefaz,
        cipher,
        clock,
        Duration.ofMinutes(cancelWindowMinutes));
  }

  @Bean
  ReturnUseCase returnUseCase(
      ReturnDocumentRepositoryPort returns,
      IssuerRepositoryPort issuers,
      DanfePort danfe,
      ReturnProcessor processor,
      ExecutorService fiscalExecutor,
      Clock clock) {
    return new ReturnService(returns, issuers, danfe, processor, fiscalExecutor, clock);
  }

  @Bean
  SaleReturnedListener saleReturnedListener(ReturnUseCase returns) {
    return new SaleReturnedListener(returns);
  }

  @Bean
  ReturnRetryScheduler returnRetryScheduler(ReturnProcessor processor) {
    return new ReturnRetryScheduler(processor);
  }

  @Bean
  NfceUseCase nfceUseCase(
      FiscalDocumentRepositoryPort documents,
      IssuerRepositoryPort issuers,
      FiscalPreferenceRepositoryPort preferences,
      SefazPort sefaz,
      DanfePort danfe,
      SecretCipherPort cipher,
      NfceProcessor processor,
      ExecutorService fiscalExecutor,
      Clock clock) {
    return new NfceService(
        documents, issuers, preferences, sefaz, danfe, cipher, processor, fiscalExecutor, clock);
  }

  @Bean
  IssuerUseCase issuerUseCase(
      IssuerRepositoryPort issuers,
      SefazPort sefaz,
      SecretCipherPort cipher,
      @Value("${fiscal.environment:2}") int environment,
      Clock clock) {
    if (environment != 1 && environment != 2) {
      throw new IllegalArgumentException(
          "FISCAL_ENVIRONMENT deve ser 1 (produção) ou 2 (homologação).");
    }
    return new IssuerService(issuers, sefaz, cipher, environment, clock);
  }

  @Bean
  SaleCompletedListener saleCompletedListener(NfceUseCase nfce) {
    return new SaleCompletedListener(nfce);
  }

  @Bean
  NfceRetryScheduler nfceRetryScheduler(NfceProcessor processor) {
    return new NfceRetryScheduler(processor);
  }

  @Bean
  AuthenticationPort authenticationPort(
      @Value("${pdv.service-url}") String url,
      @Value("${pdv.connect-timeout:2s}") Duration connectTimeout,
      @Value("${pdv.read-timeout:5s}") Duration readTimeout) {
    return new HttpAuthenticationAdapter(client(url, connectTimeout, readTimeout));
  }

  @Bean
  AddressLookupPort addressLookup() {
    return new ViaCepAddressAdapter(
        client("https://viacep.com.br", Duration.ofSeconds(3), Duration.ofSeconds(5)));
  }

  private static RestClient client(String url, Duration connectTimeout, Duration readTimeout) {
    if (url == null || !url.matches("https?://.+")) {
      throw new IllegalArgumentException("URL de serviço inválida: " + url);
    }
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return RestClient.builder()
        .baseUrl(url.replaceAll("/$", ""))
        .requestFactory(factory)
        .defaultHeader("User-Agent", "BiniTech-Fiscal/1.0")
        .build();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
