package inter.sdk;

import inter.sdk.commons.TestFixtures;
import inter.sdk.commons.TestStateReset;
import inter.sdk.commons.enums.EnvironmentEnum;
import inter.sdk.commons.utils.SslUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

public class InterSdkTest {

    private MockedStatic<SslUtils> ssl;

    @Before
    public void setUp() {
        ssl = mockStatic(SslUtils.class);
        ssl.when(() -> SslUtils.isCloseToExpire(anyString(), anyString())).thenReturn(null);
    }

    @After
    public void tearDown() {
        ssl.close();
        TestStateReset.resetAll();
    }

    private InterSdk newSdk() throws Exception {
        return new InterSdk("SANDBOX", TestFixtures.CLIENT_ID, TestFixtures.CLIENT_SECRET,
                TestFixtures.validCertPath(), TestFixtures.DUMMY_CERT_PASSWORD);
    }

    @Test
    public void shouldBuildConfigAndDefaults() throws Exception {
        InterSdk sdk = newSdk();

        assertEquals(EnvironmentEnum.SANDBOX, sdk.getConfig().getEnvironment());
        assertEquals(TestFixtures.CLIENT_ID, sdk.getConfig().getClientId());
        assertEquals(TestFixtures.CLIENT_SECRET, sdk.getConfig().getClientSecret());
        assertEquals(TestFixtures.validCertPath(), sdk.getConfig().getCertificate());
        assertTrue(sdk.getConfig().isRateLimitControl());
        assertTrue(sdk.warningList().isEmpty());
        assertTrue(new File("logs").isDirectory());
        ssl.verify(() -> SslUtils.isCloseToExpire(TestFixtures.validCertPath(), TestFixtures.DUMMY_CERT_PASSWORD));
    }

    @Test
    public void shouldCreateLogsDirectoryWhenMissing() throws Exception {
        File logs = new File("logs");
        deleteRecursively(logs.toPath());
        assertFalse(logs.exists());

        newSdk();

        assertTrue(logs.isDirectory());
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void shouldWarnWhenCertificateIsCloseToExpire() throws Exception {
        Date notAfter = new Date();
        ssl.when(() -> SslUtils.isCloseToExpire(anyString(), anyString())).thenReturn(notAfter);

        InterSdk sdk = newSdk();

        assertEquals(1, sdk.warningList().size());
        assertTrue(sdk.warningList().get(0).contains(notAfter.toString()));
        assertTrue(sdk.warningList().get(0).startsWith("Certificate nearing expiration"));
    }

    @Test
    public void shouldRotateTomorrowsLogFile() throws Exception {
        new File("logs").mkdir();
        File tomorrow = new File("logs/inter-sdk-" + LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("EEE")) + ".log");
        assertTrue(tomorrow.createNewFile() || tomorrow.exists());

        newSdk();

        assertFalse(tomorrow.exists());
    }

    @Test
    public void shouldLazilyCreateAndReuseFacades() throws Exception {
        InterSdk sdk = newSdk();

        assertSame(sdk.banking(), sdk.banking());
        assertSame(sdk.billing(), sdk.billing());
        assertSame(sdk.pix(), sdk.pix());
    }

    @Test
    public void shouldExposeConfigMutators() throws Exception {
        InterSdk sdk = newSdk();

        sdk.setDebug(true);
        sdk.setRateLimitControl(false);
        sdk.setAccount("12345");

        assertTrue(sdk.getConfig().isDebug());
        assertFalse(sdk.getConfig().isRateLimitControl());
        assertEquals("12345", sdk.getAccount());
        assertNull(newSdk().getAccount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectUnknownEnvironment() throws Exception {
        new InterSdk("NOPE", "id", "secret", "cert", "pwd");
    }
}
