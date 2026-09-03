package inter.sdk.commons.exceptions;

import inter.sdk.commons.models.Error;
import org.junit.Test;

import java.util.Date;

import static inter.sdk.commons.structures.Constants.DOC_CERTIFICATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SdkExceptionsTest {

    private final Error error = Error.builder().title("t").detail("d").build();

    @Test
    public void sdkExceptionShouldExposeErrorAndLombokContract() {
        SdkException e = new SdkException("msg", error);
        assertEquals("msg", e.getMessage());
        assertSame(error, e.getError());
        assertEquals(e, new SdkException("other", error));
        assertEquals(e.hashCode(), new SdkException("other", error).hashCode());
        assertNotEquals(e, new SdkException("msg", Error.builder().title("x").build()));
        assertNotEquals(e, new SdkException("msg", null));
        assertEquals(new SdkException("msg", null), new SdkException("msg", null));
        assertNotEquals(e, null);
        assertNotEquals(e, "string");
        assertTrue(e.canEqual(new SdkException("z", null)));
        assertTrue(e.toString().contains("SdkException(error=Error("));
        assertNull(new SdkException("m", null).getError());
    }

    @Test
    public void clientAndServerAndCertificateExceptionsShouldDelegateToSdkException() {
        assertSame(error, new ClientException("c", error).getError());
        assertEquals("c", new ClientException("c", error).getMessage());
        assertSame(error, new ServerException("s", error).getError());
        assertEquals("s", new ServerException("s", error).getMessage());
        assertSame(error, new CertificateException("x", error).getError());
        assertEquals("x", new CertificateException("x", error).getMessage());
        assertTrue(new CertificateExpiredException(new Date()) instanceof ClientException);
        assertTrue(new CertificateNotFoundException("f") instanceof ClientException);
        assertTrue(new InvalidEnvironmentException() instanceof ClientException);
    }

    @Test
    public void certificateExpiredExceptionShouldDescribeDate() {
        Date notAfter = new Date(0);
        CertificateExpiredException e = new CertificateExpiredException(notAfter);
        assertEquals("Certificate expired", e.getMessage());
        assertEquals("Certificate expired", e.getError().getTitle());
        assertEquals("Certificate expired in " + notAfter + ". Consult " + DOC_CERTIFICATE + ".", e.getError().getDetail());
    }

    @Test
    public void certificateNotFoundExceptionShouldDescribePath() {
        CertificateNotFoundException e = new CertificateNotFoundException("/tmp/x.p12");
        assertEquals("Certificate not found", e.getMessage());
        assertEquals("Certificate not found", e.getError().getTitle());
        assertEquals("Certificate not found: /tmp/x.p12. Consult " + DOC_CERTIFICATE + ".", e.getError().getDetail());
    }

    @Test
    public void invalidEnvironmentExceptionShouldListEnvironments() {
        InvalidEnvironmentException e = new InvalidEnvironmentException();
        assertEquals("Invalid environment", e.getMessage());
        assertEquals("Invalid environment", e.getError().getTitle());
        assertEquals("The environment must be one of the following: SANDBOX, PRODUCTION", e.getError().getDetail());
    }
}
