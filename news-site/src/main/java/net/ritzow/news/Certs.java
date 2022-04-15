package net.ritzow.news;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStrictStyle;
import org.bouncycastle.cert.bc.BcX509v3CertificateBuilder;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.util.AlgorithmIdentifierFactory;
import org.bouncycastle.jcajce.provider.keystore.PKCS12;
import org.bouncycastle.jcajce.provider.keystore.pkcs12.PKCS12KeyStoreSpi.BCPKCS12KeyStore;
import org.bouncycastle.operator.bc.BcContentSignerBuilder;

public class Certs {
	public static KeyStore loadPkcs12(Path p12, char[] password) 
			throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
		var ks = KeyStore.getInstance("pkcs12");

		try(var in = Files.newInputStream(p12)) {
			ks.load(in, password);
		}
		
		return ks;
	}
	
	/*public static KeyStore selfSigned(char[] password) throws IOException {

		var gen = new Ed25519KeyPairGenerator();
		//gen.init(new Ed25519KeyGenerationParameters());
		var keyPair = gen.generateKeyPair();
		
		var cert = new BcX509v3CertificateBuilder(
			X500Name.getInstance(BCStrictStyle.INSTANCE, "test-name"),
			BigInteger.valueOf(Instant.now().toEpochMilli()),
			Date.from(Instant.now()),
			Date.from(Instant.now().plus(Duration.ofHours(1))),
			X500Name.getInstance(BCStrictStyle.INSTANCE, "test-name-subj"),
			keyPair.getPublic()
		).build(null).toASN1Structure();
		
		
		
		new BcContentSignerBuilder(PKCSObjectIdentifiers.ec, );

		BCPKCS12KeyStore keyStore = new BCPKCS12KeyStore();
		keyStore.engineSetKeyEntry("ritzow.net", keyPair.getPrivate(), password, new Certificate[] {cert});
		
		return null;
	}*/
}
