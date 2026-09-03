package inter.sdk.commons.utils;

import inter.sdk.commons.TestFixtures;
import inter.sdk.commons.TestStateReset;
import inter.sdk.commons.auth.GetToken;
import inter.sdk.commons.models.Config;
import inter.sdk.commons.models.GetTokenResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Token cache behaviour; GetToken is replaced with a constructed mock so no OAuth request happens. */
public class TokenUtilsTest {

    private static final String SCOPE = "extrato.read";

    private Config config;
    private MockedConstruction<GetToken> getToken;
    private GetTokenResponse remoteToken;

    @Before
    public void setUp() {
        TestStateReset.resetAll();
        config = TestFixtures.config();
        remoteToken = TestFixtures.freshToken("remote-token", 3600);
        getToken = mockConstruction(GetToken.class,
                (mock, context) -> when(mock.get(any(Config.class), anyString())).thenReturn(remoteToken));
    }

    @After
    public void tearDown() {
        getToken.close();
        TestStateReset.resetAll();
    }

    private static String cacheKey(Config config, String scope) {
        return String.join(":", config.getClientId(), config.getClientSecret(), scope);
    }

    @Test
    public void shouldFetchAndCacheTokenOnCacheMiss() throws Exception {
        String token = TokenUtils.get(config, SCOPE);

        assertEquals("remote-token", token);
        assertEquals(1, getToken.constructed().size());
        verify(getToken.constructed().get(0)).get(config, SCOPE);
        assertSame(remoteToken, TestStateReset.tokenMap().get(cacheKey(config, SCOPE)));
    }

    @Test
    public void shouldReuseValidCachedTokenWithoutCallingGetToken() throws Exception {
        TestStateReset.tokenMap().put(cacheKey(config, SCOPE), TestFixtures.freshToken("cached-token", 3600));

        assertEquals("cached-token", TokenUtils.get(config, SCOPE));

        assertTrue(getToken.constructed().isEmpty());
    }

    @Test
    public void shouldRefreshCachedTokenThatIsAboutToExpire() throws Exception {
        // valid for less than the 60s safety margin -> treated as expired
        TestStateReset.tokenMap().put(cacheKey(config, SCOPE), TestFixtures.freshToken("stale-token", 30));

        assertEquals("remote-token", TokenUtils.get(config, SCOPE));

        assertEquals(1, getToken.constructed().size());
        assertSame(remoteToken, TestStateReset.tokenMap().get(cacheKey(config, SCOPE)));
    }

    @Test
    public void shouldKeepTokenThatExpiresJustAfterTheSafetyMargin() throws Exception {
        GetTokenResponse edge = TestFixtures.freshToken("edge-token", 60);
        edge.setCreatedAt(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + 5);
        TestStateReset.tokenMap().put(cacheKey(config, SCOPE), edge);

        assertEquals("edge-token", TokenUtils.get(config, SCOPE));

        assertTrue(getToken.constructed().isEmpty());
    }

    @Test
    public void shouldCacheTokensPerClientAndScope() throws Exception {
        TokenUtils.get(config, SCOPE);
        TokenUtils.get(config, "other.scope");
        TokenUtils.get(TestFixtures.configBuilder().clientId("other-client").build(), SCOPE);
        TokenUtils.get(config, SCOPE);

        assertEquals(3, getToken.constructed().size());
        assertEquals(3, TestStateReset.tokenMap().size());
    }

    @Test
    public void resetHelperShouldClearCache() throws Exception {
        TokenUtils.get(config, SCOPE);
        assertEquals(1, TestStateReset.tokenMap().size());
        TestStateReset.clearTokenCache();
        assertTrue(TestStateReset.tokenMap().isEmpty());
        TokenUtils.get(config, SCOPE);
        assertEquals(2, getToken.constructed().size());
        verify(getToken.constructed().get(1), times(1)).get(config, SCOPE);
        verify(getToken.constructed().get(0), never()).get(config, "unused");
    }

    @Test
    public void shouldBeInstantiable() {
        assertNotNull(new TokenUtils());
    }
}
