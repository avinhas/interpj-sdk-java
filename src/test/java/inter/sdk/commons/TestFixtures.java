package inter.sdk.commons;

import inter.sdk.commons.enums.EnvironmentEnum;
import inter.sdk.commons.models.Config;
import inter.sdk.commons.models.GetTokenResponse;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Shared test data. The {@code .p12} files are dummy self-signed keystores generated with keytool
 * (see src/test/resources/README-fixtures.md); they are NOT Banco Inter certificates.
 */
public final class TestFixtures {

    public static final String DUMMY_CERT_PASSWORD = "changeit";
    public static final String CLIENT_ID = "client-id";
    public static final String CLIENT_SECRET = "client-secret";
    public static final String ACCESS_TOKEN = "access-token-123";

    private TestFixtures() {
    }

    public static String validCertPath() {
        return resourcePath("dummy-test-cert.p12");
    }

    public static String expiredCertPath() {
        return resourcePath("dummy-expired-cert.p12");
    }

    public static String resourcePath(String name) {
        URL url = TestFixtures.class.getClassLoader().getResource(name);
        if (url == null) {
            throw new IllegalStateException("Missing test resource " + name);
        }
        return new File(url.getFile()).getAbsolutePath();
    }

    public static Config.ConfigBuilder configBuilder() {
        return Config.builder()
                .environment(EnvironmentEnum.SANDBOX)
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .certificate(validCertPath())
                .password(DUMMY_CERT_PASSWORD);
    }

    public static Config config() {
        return configBuilder().build();
    }

    public static GetTokenResponse freshToken(String accessToken, int expiresIn) {
        GetTokenResponse token = new GetTokenResponse();
        token.setAccessToken(accessToken);
        token.setTokenType("Bearer");
        token.setExpiresIn(expiresIn);
        token.setScope("scope");
        token.setCreatedAt(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        return token;
    }
}
