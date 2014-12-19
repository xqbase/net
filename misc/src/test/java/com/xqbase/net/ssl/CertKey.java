package com.xqbase.net.ssl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Enumeration;

public class CertKey implements Serializable {
	private static final long serialVersionUID = 1L;

	private RSAPrivateCrtKey key;
	private X509Certificate[] certChain;

	private void fromKeyStore(KeyStore keyStore, String password) throws Exception {
		Enumeration<String> aliases = keyStore.aliases();
		while (aliases.hasMoreElements()) {
			String alias = aliases.nextElement();
			key = (RSAPrivateCrtKey) keyStore.getKey(alias, password.toCharArray());
			if (key != null) {
				Certificate[] newCertChain = keyStore.getCertificateChain(alias);
				certChain = new X509Certificate[newCertChain.length];
				System.arraycopy(newCertChain, 0, certChain, 0, newCertChain.length);
			}
		}
	}

	public CertKey(KeyStore keyStore, String password) throws Exception {
		fromKeyStore(keyStore, password);
	}

	public CertKey(RSAPrivateCrtKey key, X509Certificate... certChain) {
		this.key = key;
		this.certChain = certChain;
	}

	public KeyStore toKeyStore(String password, String type)
			throws IOException, GeneralSecurityException {
		KeyStore keyStore = KeyStore.getInstance(type);
		keyStore.load(null, null);
		keyStore.setKeyEntry("", key, password.toCharArray(), certChain);
		return keyStore;
	}

	public void toKeyStore(File fileJks, String password, String type)
			throws IOException, GeneralSecurityException {
		try (FileOutputStream outJks = new FileOutputStream(fileJks)) {
			toKeyStore(password, type).store(outJks, password.toCharArray());
		}
	}

	public RSAPrivateCrtKey getKey() {
		return key;
	}

	public X509Certificate[] getCertificateChain() {
		return certChain;
	}
}