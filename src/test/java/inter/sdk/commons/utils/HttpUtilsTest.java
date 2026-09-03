package inter.sdk.commons.utils;

import inter.sdk.commons.TestFixtures;
import inter.sdk.commons.TestStateReset;
import inter.sdk.commons.exceptions.CertificateException;
import inter.sdk.commons.exceptions.ClientException;
import inter.sdk.commons.exceptions.SdkException;
import inter.sdk.commons.exceptions.ServerException;
import inter.sdk.commons.models.Config;
import inter.sdk.commons.models.Error;
import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStoreException;

import static inter.sdk.commons.TestFixtures.ACCESS_TOKEN;
import static inter.sdk.commons.structures.Constants.CERTIFICATE_EXCEPTION_MESSAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HttpUtils with SSL, token retrieval and the Apache client all mocked away: nothing here opens a socket.
 */
public class HttpUtilsTest {

    private static final String URL = "https://example.invalid/banking/v2/saldo";
    private static final String SCOPE = "extrato.read";
    private static final String MESSAGE = "Error doing thing";

    private MockedStatic<TokenUtils> tokenUtils;
    private MockedStatic<SslUtils> sslUtils;
    private MockedStatic<HttpClients> httpClients;
    private CloseableHttpClient httpClient;
    private Config config;

    @Before
    public void setUp() throws Exception {
        config = TestFixtures.configBuilder().account("12345").debug(true).build();
        tokenUtils = mockStatic(TokenUtils.class);
        tokenUtils.when(() -> TokenUtils.get(any(Config.class), anyString())).thenReturn(ACCESS_TOKEN);
        sslUtils = mockStatic(SslUtils.class);
        sslUtils.when(() -> SslUtils.buildConnectionManager(anyString(), anyString()))
                .thenReturn(mock(BasicHttpClientConnectionManager.class));
        httpClient = mock(CloseableHttpClient.class);
        HttpClientBuilder builder = mock(HttpClientBuilder.class);
        when(builder.setConnectionManager(any())).thenReturn(builder);
        when(builder.build()).thenReturn(httpClient);
        httpClients = mockStatic(HttpClients.class);
        httpClients.when(HttpClients::custom).thenReturn(builder);
    }

    @After
    public void tearDown() {
        httpClients.close();
        sslUtils.close();
        tokenUtils.close();
        TestStateReset.resetAll();
    }

    static CloseableHttpResponse response(int status, String reason, String body) {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, status, reason));
        when(response.getEntity()).thenReturn(body == null ? null : new StringEntity(body, StandardCharsets.UTF_8));
        return response;
    }

    private void respondWith(CloseableHttpResponse first, CloseableHttpResponse... rest) throws IOException {
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(first, rest);
    }

    private HttpUriRequest capturedRequest() throws IOException {
        ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(httpClient).execute(captor.capture());
        return captor.getValue();
    }

    // ---- handleResponse -------------------------------------------------------------------------

    @Test
    public void handleResponseShouldReturnFalseOn2xx() throws Exception {
        assertFalse(HttpUtils.handleResponse(URL, response(200, "OK", "{}"), MESSAGE, false));
        assertFalse(HttpUtils.handleResponse(URL, response(204, "No Content", null), MESSAGE, true));
    }

    @Test
    public void handleResponseShouldReturnTrueOn429WhenRateLimitControlIsOn() throws Exception {
        assertTrue(HttpUtils.handleResponse(URL, response(429, "Too Many Requests", ""), MESSAGE, true));
    }

    @Test
    public void handleResponseShouldThrowClientExceptionOn429WhenRateLimitControlIsOff() throws Exception {
        try {
            HttpUtils.handleResponse(URL, response(429, "Too Many Requests", ""), MESSAGE, false);
            fail();
        } catch (ClientException e) {
            assertEquals(MESSAGE, e.getMessage());
            assertEquals("HTTP/1.1 429 Too Many Requests", e.getError().getTitle());
        }
    }

    @Test
    public void handleResponseShouldThrowClientExceptionWithParsedErrorOn4xx() throws Exception {
        String body = "{\"title\":\"Bad\",\"detail\":\"Field x\",\"violacoes\":[{\"razao\":\"r\",\"propriedade\":\"p\",\"valor\":\"v\"}]}";
        try {
            HttpUtils.handleResponse(URL, response(400, "Bad Request", body), MESSAGE, true);
            fail();
        } catch (ClientException e) {
            assertEquals(MESSAGE, e.getMessage());
            assertEquals("Bad", e.getError().getTitle());
            assertEquals("Field x", e.getError().getDetail());
            assertEquals(1, e.getError().getViolations().size());
            assertEquals("r", e.getError().getViolations().get(0).getReason());
        }
    }

    @Test
    public void handleResponseShouldFallBackToStatusLineWhen4xxBodyIsNotJson() throws Exception {
        try {
            HttpUtils.handleResponse(URL, response(404, "Not Found", "<html>nope</html>"), MESSAGE, false);
            fail();
        } catch (ClientException e) {
            assertEquals("HTTP/1.1 404 Not Found", e.getError().getTitle());
            assertNull(e.getError().getDetail());
            assertNull(e.getError().getViolations());
        }
    }

    @Test
    public void handleResponseShouldThrowServerExceptionWithParsedErrorOn5xx() throws Exception {
        try {
            HttpUtils.handleResponse(URL, response(500, "Internal Server Error", "{\"title\":\"Boom\",\"detail\":\"d\"}"), MESSAGE, false);
            fail();
        } catch (ServerException e) {
            assertEquals(MESSAGE, e.getMessage());
            assertEquals("Boom", e.getError().getTitle());
            assertEquals("d", e.getError().getDetail());
        }
    }

    @Test
    public void handleResponseShouldUseStatusLineWhen5xxBodyIsEmpty() throws Exception {
        try {
            HttpUtils.handleResponse(URL, response(503, "Service Unavailable", ""), MESSAGE, false);
            fail();
        } catch (ServerException e) {
            assertEquals("HTTP/1.1 503 Service Unavailable", e.getError().getTitle());
        }
    }

    @Test
    public void convertJsonToErrorShouldParseError() throws Exception {
        Error error = HttpUtils.convertJsonToError("{\"title\":\"t\",\"detail\":\"d\",\"timestamp\":\"ts\"}");
        assertEquals("t", error.getTitle());
        assertEquals("d", error.getDetail());
        assertEquals("ts", error.getTimestamp());
    }

    // ---- call* ------------------------------------------------------------------------------------

    @Test
    public void callGetShouldAddHeadersAndReturnBody() throws Exception {
        respondWith(response(200, "OK", "{\"disponivel\":10}"));

        String body = HttpUtils.callGet(config, URL, SCOPE, MESSAGE);

        assertEquals("{\"disponivel\":10}", body);
        assertEquals(URL, HttpUtils.getLastUrl());
        assertNull(HttpUtils.getLastRequest());
        HttpUriRequest request = capturedRequest();
        assertEquals("GET", request.getMethod());
        assertEquals(URL, request.getURI().toString());
        assertEquals("Bearer " + ACCESS_TOKEN, header(request, "Authorization"));
        assertEquals("12345", header(request, "x-conta-corrente"));
        assertEquals("java", header(request, "x-inter-sdk"));
        assertEquals("1.0.2", header(request, "x-inter-sdk-version"));
        tokenUtils.verify(() -> TokenUtils.get(config, SCOPE));
        sslUtils.verify(() -> SslUtils.buildConnectionManager(config.getCertificate(), config.getPassword()));
    }

    @Test
    public void callGetShouldOmitAccountHeaderWhenAccountIsNull() throws Exception {
        Config noAccount = TestFixtures.config();
        respondWith(response(200, "OK", null));

        assertNull(HttpUtils.callGet(noAccount, URL, SCOPE, MESSAGE));

        assertNull(header(capturedRequest(), "x-conta-corrente"));
    }

    @Test
    public void callPostShouldSendJsonBodyWithContentType() throws Exception {
        respondWith(response(201, "Created", "{\"ok\":true}"));

        String body = HttpUtils.callPost(config, URL, SCOPE, MESSAGE, "{\"a\":1}");

        assertEquals("{\"ok\":true}", body);
        assertEquals("{\"a\":1}", HttpUtils.getLastRequest());
        HttpEntityEnclosingRequestBase request = (HttpEntityEnclosingRequestBase) capturedRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("application/json", header(request, "Content-Type"));
        assertEquals("{\"a\":1}", EntityUtils.toString(request.getEntity(), StandardCharsets.UTF_8));
    }

    @Test
    public void callPutShouldUsePutMethod() throws Exception {
        respondWith(response(200, "OK", ""));
        assertEquals("", HttpUtils.callPut(TestFixtures.config(), URL, SCOPE, MESSAGE, "{}"));
        assertEquals("PUT", capturedRequest().getMethod());
    }

    @Test
    public void callPatchShouldUsePatchMethod() throws Exception {
        respondWith(response(200, "OK", "patched"));
        assertEquals("patched", HttpUtils.callPatch(config, URL, SCOPE, MESSAGE, "{}"));
        assertEquals("PATCH", capturedRequest().getMethod());
    }

    @Test
    public void callDeleteShouldUseDeleteMethod() throws Exception {
        respondWith(response(204, "No Content", null));
        assertNull(HttpUtils.callDelete(config, URL, SCOPE, MESSAGE));
        HttpUriRequest request = capturedRequest();
        assertEquals("DELETE", request.getMethod());
        assertEquals("Bearer " + ACCESS_TOKEN, header(request, "Authorization"));
    }

    @Test
    public void callShouldPropagateClientException() throws Exception {
        respondWith(response(403, "Forbidden", "{\"title\":\"Denied\"}"));
        try {
            HttpUtils.callGet(config, URL, SCOPE, MESSAGE);
            fail();
        } catch (ClientException e) {
            assertEquals("Denied", e.getError().getTitle());
        }
    }

    @Test
    public void callShouldPropagateServerException() throws Exception {
        respondWith(response(502, "Bad Gateway", ""));
        try {
            HttpUtils.callDelete(config, URL, SCOPE, MESSAGE);
            fail();
        } catch (ServerException e) {
            assertEquals("HTTP/1.1 502 Bad Gateway", e.getError().getTitle());
        }
    }

    /**
     * Mockito refuses {@code mockStatic(Thread.class)} ("not possible to mock static methods of java.lang.Thread"),
     * so the 60s back-off is short-circuited by pre-interrupting the thread: {@code Thread.sleep} then throws
     * {@link InterruptedException} immediately, which HttpUtils wraps in a RuntimeException.
     */
    @Test
    public void callShouldEnterRetryBranchOn429WithoutSleepingSixtySeconds() throws Exception {
        Config rateLimited = TestFixtures.configBuilder().rateLimitControl(true).build();
        respondWith(response(429, "Too Many Requests", ""), response(200, "OK", "late"));
        long start = System.currentTimeMillis();
        Thread.currentThread().interrupt();
        try {
            HttpUtils.callGet(rateLimited, URL, SCOPE, MESSAGE);
            fail();
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof InterruptedException);
        } finally {
            assertFalse(Thread.interrupted());
        }
        assertTrue(System.currentTimeMillis() - start < 10_000);
        verify(httpClient, times(1)).execute(any(HttpUriRequest.class));
    }

    @Test
    public void callShouldNotRetryOn429WhenRateLimitControlIsOff() throws Exception {
        respondWith(response(429, "Too Many Requests", ""));
        try {
            HttpUtils.callGet(config, URL, SCOPE, MESSAGE);
            fail();
        } catch (ClientException e) {
            assertEquals("HTTP/1.1 429 Too Many Requests", e.getError().getTitle());
        }
        verify(httpClient, times(1)).execute(any(HttpUriRequest.class));
    }

    @Test
    public void callShouldWrapIoExceptionAsSdkException() throws Exception {
        when(httpClient.execute(any(HttpUriRequest.class))).thenThrow(new IOException("connection reset"));
        try {
            HttpUtils.callGet(config, URL, SCOPE, MESSAGE);
            fail();
        } catch (SdkException e) {
            assertEquals("connection reset", e.getMessage());
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
            assertEquals("connection reset", e.getError().getDetail());
        }
    }

    @Test
    public void callShouldWrapSecurityExceptionsAsCertificateException() throws Exception {
        tokenUtils.when(() -> TokenUtils.get(any(Config.class), anyString())).thenThrow(new KeyStoreException("bad keystore"));
        try {
            HttpUtils.callGet(config, URL, SCOPE, MESSAGE);
            fail();
        } catch (CertificateException e) {
            assertEquals("bad keystore", e.getMessage());
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
            assertEquals("bad keystore", e.getError().getDetail());
        }
        verify(httpClient, never()).execute(any(HttpUriRequest.class));
    }

    @Test
    public void shouldBeInstantiable() {
        assertNotNull(new HttpUtils());
    }


    private static String header(HttpUriRequest request, String name) {
        Header header = request.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }
}
