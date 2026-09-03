package inter.sdk.commons;

import inter.sdk.commons.exceptions.SdkException;
import inter.sdk.commons.models.Config;
import inter.sdk.commons.utils.HttpUtils;
import org.junit.After;
import org.junit.Before;
import org.mockito.MockedStatic;

import static inter.sdk.commons.structures.Constants.CERTIFICATE_EXCEPTION_MESSAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Base class for {@code *Client} tests: HttpUtils is statically mocked so no request ever leaves the JVM,
 * and the static SDK state is reset after each test.
 */
public abstract class ClientTestSupport {

    protected static final String BASE = "https://cdpj-sandbox.partners.uatinter.co";
    protected static final String NOT_JSON = "<html>not json</html>";

    protected MockedStatic<HttpUtils> http;
    protected Config config;

    @Before
    public void setUpHttp() {
        config = TestFixtures.config();
        http = mockStatic(HttpUtils.class);
    }

    @After
    public void tearDownHttp() {
        http.close();
        TestStateReset.resetAll();
    }

    protected void onGet(String url, String scope, String json) {
        http.when(() -> HttpUtils.callGet(eq(config), eq(url), eq(scope), anyString())).thenReturn(json);
    }

    protected void onDelete(String url, String scope, String json) {
        http.when(() -> HttpUtils.callDelete(eq(config), eq(url), eq(scope), anyString())).thenReturn(json);
    }

    protected void onPost(String url, String scope, String json) {
        http.when(() -> HttpUtils.callPost(eq(config), eq(url), eq(scope), anyString(), anyString())).thenReturn(json);
    }

    protected void onPut(String url, String scope, String json) {
        http.when(() -> HttpUtils.callPut(eq(config), eq(url), eq(scope), anyString(), anyString())).thenReturn(json);
    }

    protected void onPatch(String url, String scope, String json) {
        http.when(() -> HttpUtils.callPatch(eq(config), eq(url), eq(scope), anyString(), anyString())).thenReturn(json);
    }

    protected void verifyGet(String url, String scope, String message) {
        http.verify(() -> HttpUtils.callGet(eq(config), eq(url), eq(scope), eq(message)));
    }

    protected void verifyDelete(String url, String scope, String message) {
        http.verify(() -> HttpUtils.callDelete(eq(config), eq(url), eq(scope), eq(message)));
    }

    protected void verifyPost(String url, String scope, String message, String... bodyFragments) {
        http.verify(() -> HttpUtils.callPost(eq(config), eq(url), eq(scope), eq(message), containing(bodyFragments)));
    }

    protected void verifyPut(String url, String scope, String message, String... bodyFragments) {
        http.verify(() -> HttpUtils.callPut(eq(config), eq(url), eq(scope), eq(message), containing(bodyFragments)));
    }

    protected void verifyPatch(String url, String scope, String message, String... bodyFragments) {
        http.verify(() -> HttpUtils.callPatch(eq(config), eq(url), eq(scope), eq(message), containing(bodyFragments)));
    }

    protected static String containing(String... fragments) {
        return argThat(body -> {
            if (body == null) {
                return false;
            }
            for (String fragment : fragments) {
                if (!body.contains(fragment)) {
                    return false;
                }
            }
            return true;
        });
    }

    protected interface SdkCall {
        void run() throws SdkException;
    }

    /** Asserts the client wraps a JSON parse failure into the SDK's generic SdkException. */
    protected static void assertWrapsParseFailure(SdkCall call) {
        try {
            call.run();
            fail("expected SdkException");
        } catch (SdkException e) {
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
            assertEquals(e.getMessage(), e.getError().getDetail());
        }
    }

    protected static void assertPropagates(SdkException expected, SdkCall call) {
        try {
            call.run();
            fail("expected SdkException");
        } catch (SdkException e) {
            assertEquals(expected, e);
        }
    }
}
