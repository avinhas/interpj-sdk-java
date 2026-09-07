package inter.sdk.commons.utils;

import inter.sdk.commons.TestFixtures;
import inter.sdk.commons.exceptions.CertificateException;
import inter.sdk.commons.exceptions.CertificateExpiredException;
import inter.sdk.commons.exceptions.CertificateNotFoundException;
import inter.sdk.commons.exceptions.SdkException;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static inter.sdk.commons.TestFixtures.DUMMY_CERT_PASSWORD;
import static inter.sdk.commons.structures.Constants.CERTIFICATE_EXCEPTION_MESSAGE;
import static inter.sdk.commons.structures.Constants.DOC_CERTIFICATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Exercises SslUtils against REAL self-signed PKCS12 fixtures (no network, not Banco Inter certs).
 */
public class SslUtilsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void shouldBuildConnectionManagerFromValidDummyCertificate() throws SdkException {
        BasicHttpClientConnectionManager manager =
                SslUtils.buildConnectionManager(TestFixtures.validCertPath(), DUMMY_CERT_PASSWORD);
        assertNotNull(manager);
        manager.close();
    }

    @Test
    public void shouldThrowCertificateNotFoundWhenFileIsMissing() {
        String missing = new File(tmp.getRoot(), "does-not-exist.p12").getAbsolutePath();
        try {
            SslUtils.buildConnectionManager(missing, DUMMY_CERT_PASSWORD);
            fail("expected CertificateNotFoundException");
        } catch (CertificateNotFoundException e) {
            assertEquals("Certificate not found", e.getMessage());
            assertEquals("Certificate not found", e.getError().getTitle());
            assertEquals("Certificate not found: " + missing + ". Consult " + DOC_CERTIFICATE + ".", e.getError().getDetail());
        } catch (SdkException e) {
            fail("unexpected " + e);
        }
    }

    @Test
    public void shouldThrowCertificateExpiredWhenCertificateIsExpired() {
        try {
            SslUtils.buildConnectionManager(TestFixtures.expiredCertPath(), DUMMY_CERT_PASSWORD);
            fail("expected CertificateExpiredException");
        } catch (CertificateExpiredException e) {
            assertEquals("Certificate expired", e.getMessage());
            assertEquals("Certificate expired", e.getError().getTitle());
            assertTrue(e.getError().getDetail().startsWith("Certificate expired in "));
            assertTrue(e.getError().getDetail().endsWith("Consult " + DOC_CERTIFICATE + "."));
        } catch (SdkException e) {
            fail("unexpected " + e);
        }
    }

    @Test
    public void shouldThrowSdkExceptionWhenPasswordIsWrong() {
        try {
            SslUtils.buildConnectionManager(TestFixtures.validCertPath(), "wrong-password");
            fail("expected SdkException");
        } catch (SdkException e) {
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
            assertNotNull(e.getError().getDetail());
            assertEquals(e.getMessage(), e.getError().getDetail());
        }
    }

    @Test
    public void shouldLoadKeyStore() throws Exception {
        KeyStore keyStore = SslUtils.getKeyStore(TestFixtures.validCertPath(), DUMMY_CERT_PASSWORD);
        assertNotNull(keyStore);
        assertTrue(keyStore.containsAlias("dummy-test-cert"));
    }

    @Test
    public void shouldReportNotCloseToExpireForLongLivedCertificate() throws SdkException {
        assertNull(SslUtils.isCloseToExpire(TestFixtures.validCertPath(), DUMMY_CERT_PASSWORD));
    }

    @Test
    public void shouldReturnNotAfterWhenCertificateExpiresWithinThirtyDays() throws Exception {
        File soon = generateShortLivedKeystore();
        Date notAfter = SslUtils.isCloseToExpire(soon.getAbsolutePath(), DUMMY_CERT_PASSWORD);
        assertNotNull(notAfter);
        assertTrue(notAfter.after(new Date()));
        assertTrue(notAfter.before(new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30))));
        assertNotNull(SslUtils.buildConnectionManager(soon.getAbsolutePath(), DUMMY_CERT_PASSWORD));
    }

    @Test(expected = CertificateExpiredException.class)
    public void shouldFailIsCloseToExpireForExpiredCertificate() throws SdkException {
        SslUtils.isCloseToExpire(TestFixtures.expiredCertPath(), DUMMY_CERT_PASSWORD);
    }

    @Test
    public void shouldBeInstantiable() {
        assertNotNull(new SslUtils());
    }

    @Test
    public void shouldWrapKeyStoreExceptionFromGetInstance() {
        try (MockedStatic<KeyStore> keyStores = mockStatic(KeyStore.class)) {
            keyStores.when(() -> KeyStore.getInstance("pkcs12")).thenThrow(new KeyStoreException("no pkcs12"));
            SslUtils.getKeyStore(TestFixtures.validCertPath(), DUMMY_CERT_PASSWORD);
            fail("expected CertificateException");
        } catch (CertificateException e) {
            assertEquals("no pkcs12", e.getMessage());
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
            assertEquals("no pkcs12", e.getError().getDetail());
        } catch (SdkException e) {
            fail("unexpected " + e);
        }
    }

    @Test
    public void shouldWrapKeyStoreExceptionWhileCheckingExpiration() throws Exception {
        KeyStore broken = mock(KeyStore.class);
        when(broken.aliases()).thenThrow(new KeyStoreException("aliases failed"));
        try (MockedStatic<SslUtils> ssl = mockStatic(SslUtils.class, CALLS_REAL_METHODS)) {
            ssl.when(() -> SslUtils.getKeyStore(anyString(), anyString())).thenReturn(broken);
            SslUtils.isCloseToExpire(TestFixtures.validCertPath(), DUMMY_CERT_PASSWORD);
            fail("expected CertificateException");
        } catch (CertificateException e) {
            assertEquals("aliases failed", e.getMessage());
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
        }
    }

    @Test
    public void shouldWrapKeyStoreExceptionWhileBuildingSslContext() throws Exception {
        KeyStore broken = mock(KeyStore.class);
        when(broken.aliases())
                .thenReturn(Collections.<String>emptyEnumeration())
                .thenThrow(new KeyStoreException("key manager failed"));
        try (MockedStatic<SslUtils> ssl = mockStatic(SslUtils.class, CALLS_REAL_METHODS)) {
            ssl.when(() -> SslUtils.getKeyStore(anyString(), anyString())).thenReturn(broken);
            SslUtils.buildConnectionManager(TestFixtures.validCertPath(), DUMMY_CERT_PASSWORD);
            fail("expected CertificateException");
        } catch (CertificateException e) {
            assertEquals("key manager failed", e.getMessage());
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
        }
    }

    /** A cert that is valid now but expires in 5 days cannot be committed, so it is generated with keytool at test time. */
    private File generateShortLivedKeystore() throws Exception {
        File keytool = locateKeytool();
        assumeTrue("keytool not available", keytool != null);
        File out = tmp.newFile("dummy-soon-cert.p12");
        assertTrue(out.delete());
        Process p = new ProcessBuilder(keytool.getAbsolutePath(),
                "-genkeypair", "-alias", "dummy-soon-cert", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", out.getAbsolutePath(),
                "-storepass", DUMMY_CERT_PASSWORD, "-keypass", DUMMY_CERT_PASSWORD,
                "-dname", "CN=Dummy Soon Expiring Fixture, O=NOT-A-REAL-BANCO-INTER-CERT, C=BR",
                "-validity", "5")
                .redirectErrorStream(true)
                .start();
        assumeTrue("keytool failed", p.waitFor() == 0 && out.exists());
        return out;
    }

    private static File locateKeytool() {
        String javaHome = System.getProperty("java.home");
        String exe = System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool";
        File[] candidates = {
                new File(javaHome, "bin/" + exe),
                new File(new File(javaHome).getParentFile(), "bin/" + exe)
        };
        for (File c : candidates) {
            if (c.canExecute()) {
                return c;
            }
        }
        return null;
    }
}
