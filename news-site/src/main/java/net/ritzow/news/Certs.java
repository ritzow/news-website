package net.ritzow.news;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.sql.Time;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.io.SignerOutputStream;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.slf4j.LoggerFactory;

public class Certs {
	public static KeyStore loadPkcs12(Path p12, char[] password) 
			throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
		var ks = KeyStore.getInstance("pkcs12");

		try(var in = Files.newInputStream(p12)) {
			ks.load(in, password);
		}
		
		return ks;
	}
	
	public static KeyStore selfSigned(String host, char[] password) throws IOException {
		try {
			var privateKey = new Ed25519PrivateKeyParameters(SecureRandom.getInstanceStrong());
			var publicKey = privateKey.generatePublicKey();

			var issuer = new X500Name(new RDN[] {segment("C", "US"), segment("O", "Solomon Ritzow")});
			var subject = new X500Name(new RDN[] {segment("CN", host)});
			
			var signer = new ContentSigner() {
				private final Ed25519Signer signer;

				{
					this.signer = new Ed25519Signer();
					signer.init(true, privateKey);
				}
				
				@Override
				public AlgorithmIdentifier getAlgorithmIdentifier() {
					/* Defined in https://www.rfc-editor.org/rfc/rfc8419.html#page-2, same as OID */
					return new AlgorithmIdentifier(new ASN1ObjectIdentifier("1.3.101.112"));
				}

				@Override
				public OutputStream getOutputStream() {
					return new SignerOutputStream(signer);
				}

				@Override
				public byte[] getSignature() {
					return signer.generateSignature();
				}
			};
			
			var now = Instant.now();
				
			var cert = new X509v3CertificateBuilder(
				issuer,
				BigInteger.valueOf(Instant.now().toEpochMilli()),
				Time.from(now),
				Time.from(now.plus(Duration.ofHours(1))),
				subject,
				SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(publicKey)
			)
				.addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
				.addExtension(Extension.subjectAlternativeName, false, 
					new GeneralNames(new GeneralName(GeneralName.dNSName, new DERIA5String(host))))
				.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature))
				.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))
				.build(signer);

			/*LoggerFactory.getLogger(Certs.class).atInfo().log("Writing PEM file");
			try(var writer = new PemWriter(Files.newBufferedWriter(Path.of("certtest.pem")))) {
				writer.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
			}*/
			
			var certChain = new Certificate[] {new JcaX509CertificateConverter().getCertificate(cert)};
			
			var jcaPrivateKey = new JcaPEMKeyConverter()
				.getPrivateKey(PrivateKeyInfoFactory.createPrivateKeyInfo(privateKey));
			
			var keyStore = KeyStore.Builder.newInstance("PKCS12", null, new PasswordProtection(password))
				.getKeyStore();
			
			//KeyStore keyStore = KeyStore.getInstance("PKCS12");
			keyStore.setEntry(host, new PrivateKeyEntry(jcaPrivateKey, 
				certChain, Set.of()), new PasswordProtection(password));
			
			return keyStore;
		} catch(
			NoSuchAlgorithmException |
			KeyStoreException |
			CertificateException e) {
			throw new IOException(e);
		}
	}
	
	private static RDN segment(String key, String value) {
		return new RDN(X500Name.getDefaultStyle().attrNameToOID(key), new DERUTF8String(value));
	}
}
