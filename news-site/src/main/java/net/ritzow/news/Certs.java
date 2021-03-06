package net.ritzow.news;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.sql.Time;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jcajce.provider.asymmetric.util.PrimeCertaintyCalculator;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;

public class Certs {
	public static KeyStore loadPkcs12(Path p12, char[] password) 
			throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
		var ks = KeyStore.getInstance("pkcs12");

		try(var in = Files.newInputStream(p12)) {
			ks.load(in, password);
		}
		
		return ks;
	}

	private static byte[] longToBytes(long l) {
		byte[] result = new byte[Long.BYTES];
		for (int i = Long.BYTES - 1; i >= 0; i--) {
			result[i] = (byte)(l & 0xFF);
			l >>= Byte.SIZE;
		}
		return result;
	}

	//TODO allow multiple hostnames
	public static KeyStore selfSigned(String host, String org, char[] password) throws IOException {
		try {
			var gen = new RSAKeyPairGenerator();
			var seed = MessageDigest.getInstance("SHA256");
			ByteBuffer passwordBytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
			seed.update(passwordBytes);
			passwordBytes.clear();
			//clear the password
			while(passwordBytes.hasRemaining()) {
				passwordBytes.put((byte)0);
			}

			var from = Instant.now().truncatedTo(ChronoUnit.HOURS);
			
			seed.update(longToBytes(from.toEpochMilli()));

			var random = new SecureRandom(seed.digest());

			int keySize = 2048;
			gen.init(new RSAKeyGenerationParameters(
				BigInteger.valueOf(0x10001), 
				random,
				keySize, PrimeCertaintyCalculator.getDefaultCertainty(keySize)));
			var pair = gen.generateKeyPair();
			var privateKey = pair.getPrivate();
			var publicKey = pair.getPublic();

			//TODO make subject match issuer (self-signed)
			var issuer = new X500Name(new RDN[] {segment("C", "US"), segment("O", org)});
			
			var sigAlgId = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption);
			var signer = new BcRSAContentSignerBuilder(sigAlgId, 
				new AlgorithmIdentifier(new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId).getAlgorithm()))
				.build(privateKey);
			
			MessageDigest serialHash = MessageDigest.getInstance("SHA256");
			serialHash.update(passwordBytes);
			serialHash.update(host.getBytes(StandardCharsets.UTF_8));
			serialHash.update(org.getBytes(StandardCharsets.UTF_8));
			serialHash.update(longToBytes(from.toEpochMilli()));
			BigInteger serial = new BigInteger(1, serialHash.digest());
				
			var cert = new X509v3CertificateBuilder(
				issuer,
				serial,
				Time.from(from),
				Time.from(from.plus(Duration.ofHours(1))),
				issuer,
				SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(publicKey)
			)
				.addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
				.addExtension(Extension.subjectAlternativeName, false, 
					//TODO if host is an IP address, use different general name type
					new GeneralNames(new GeneralName(GeneralName.dNSName, new DERIA5String(host))))
				.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature))
				.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))
				.build(signer);
			
			var certChain = new Certificate[] {new JcaX509CertificateConverter().getCertificate(cert)};
			
			var jcaPrivateKey = new JcaPEMKeyConverter()
				.getPrivateKey(PrivateKeyInfoFactory.createPrivateKeyInfo(privateKey));
			
			var keyStore = KeyStore.Builder.newInstance("PKCS12", null, new PasswordProtection(password))
				.getKeyStore();
			
			//KeyStore keyStore = KeyStore.getInstance("PKCS12");
			keyStore.setEntry(host, new PrivateKeyEntry(jcaPrivateKey, 
				certChain, Set.of()), new PasswordProtection(password));
			
			return keyStore;
		} catch(NoSuchAlgorithmException | KeyStoreException | CertificateException | OperatorCreationException e) {
			throw new IOException(e);
		}
	}
	
	private static RDN segment(String key, String value) {
		return new RDN(X500Name.getDefaultStyle().attrNameToOID(key), new DERUTF8String(value));
	}
}
