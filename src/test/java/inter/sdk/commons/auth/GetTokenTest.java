package inter.sdk.commons.auth;

import inter.sdk.commons.TestFixtures;
import inter.sdk.commons.TestStateReset;
import inter.sdk.commons.enums.EnvironmentEnum;
import inter.sdk.commons.exceptions.ClientException;
import inter.sdk.commons.models.Config;
import inter.sdk.commons.models.GetTokenResponse;
import inter.sdk.commons.utils.SslUtils;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static inter.sdk.commons.structures.Constants.URL_TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** OAuth token retrieval with SSL and the Apache client mocked: no live OAuth call is ever made. */
public class GetTokenTest {

    private MockedStatic<SslUtils> sslUtils;
    private MockedStatic<HttpClients> httpClients;
    private CloseableHttpClient httpClient;
    private Config config;

    @Before
    public void setUp() throws Exception {
        config = TestFixtures.configBuilder().environment(EnvironmentEnum.UAT).build();
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
        TestStateReset.resetAll();
    }

    private static CloseableHttpResponse response(int status, String reason, String body) {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, status, reason));
        when(response.getEntity()).thenReturn(new StringEntity(body, StandardCharsets.UTF_8));
        return response;
    }

    @Test
    public void shouldPostClientCredentialsAndParseToken() throws Exception {
        CloseableHttpResponse ok = response(200, "OK",
                "{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"extrato.read\"}");
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(ok);
        long before = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

        GetTokenResponse token = new GetToken().get(config, "extrato.read");

        assertEquals("tok", token.getAccessToken());
        assertEquals("Bearer", token.getTokenType());
        assertEquals(Integer.valueOf(3600), token.getExpiresIn());
        assertEquals("extrato.read", token.getScope());
        assertTrue(token.getCreatedAt() >= before);
        assertTrue(token.getCreatedAt() <= LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));

        ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(httpClient).execute(captor.capture());
        HttpPost request = (HttpPost) captor.getValue();
        assertEquals(EnvironmentEnum.UAT.getUrlBase() + URL_TOKEN, request.getURI().toString());
        assertEquals("application/x-www-form-urlencoded", request.getFirstHeader("Content-Type").getValue());
        String form = EntityUtils.toString(request.getEntity(), StandardCharsets.UTF_8);
        assertEquals("client_id=client-id&client_secret=client-secret&grant_type=client_credentials&scope=extrato.read", form);
        sslUtils.verify(() -> SslUtils.buildConnectionManager(config.getCertificate(), config.getPassword()));
    }

    @Test
    public void shouldPropagateClientExceptionOnErrorStatus() throws Exception {
        CloseableHttpResponse unauthorized = response(401, "Unauthorized",
                "{\"title\":\"invalid_client\",\"detail\":\"bad secret\"}");
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(unauthorized);
        try {
            new GetToken().get(config, "extrato.read");
            fail();
        } catch (ClientException e) {
            assertEquals("Error retrieving token", e.getMessage());
            assertEquals("invalid_client", e.getError().getTitle());
            assertEquals("bad secret", e.getError().getDetail());
        }
    }
}
