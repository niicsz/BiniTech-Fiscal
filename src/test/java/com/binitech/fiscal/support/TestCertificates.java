package com.binitech.fiscal.support;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class TestCertificates {

  private TestCertificates() {}

  public static byte[] pfx(String cnpj, String name, String password, int validDays)
      throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    X500Name subject = new X500Name("CN=" + name + ":" + cnpj + ", O=ICP-Brasil, C=BR");
    Instant now = Instant.now();
    JcaX509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now.minus(1, ChronoUnit.DAYS)),
            Date.from(now.plus(validDays, ChronoUnit.DAYS)),
            subject,
            keyPair.getPublic());
    builder.addExtension(
        Extension.keyUsage,
        true,
        new KeyUsage(
            KeyUsage.digitalSignature | KeyUsage.keyEncipherment | KeyUsage.nonRepudiation));
    GeneralName cnpjName =
        new GeneralName(
            GeneralName.otherName,
            new DERSequence(
                new ASN1Encodable[] {
                  new ASN1ObjectIdentifier("2.16.76.1.3.3"),
                  new DERTaggedObject(true, 0, new DEROctetString(cnpj.getBytes()))
                }));
    builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(cnpjName));
    X509Certificate certificate =
        new JcaX509CertificateConverter()
            .getCertificate(
                builder.build(
                    new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, null);
    keyStore.setKeyEntry(
        name, keyPair.getPrivate(), password.toCharArray(), new X509Certificate[] {certificate});
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    keyStore.store(out, password.toCharArray());
    return out.toByteArray();
  }
}
