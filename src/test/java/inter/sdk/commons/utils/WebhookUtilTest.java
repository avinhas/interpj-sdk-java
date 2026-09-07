package inter.sdk.commons.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import inter.sdk.commons.JsonFailure;
import inter.sdk.commons.TestFixtures;
import inter.sdk.commons.TestStateReset;
import inter.sdk.commons.exceptions.ClientException;
import inter.sdk.commons.exceptions.SdkException;
import inter.sdk.commons.models.Config;
import inter.sdk.commons.models.Error;
import inter.sdk.commons.models.IncludeWebhookRequest;
import inter.sdk.commons.models.Webhook;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static inter.sdk.commons.structures.Constants.CERTIFICATE_EXCEPTION_MESSAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

public class WebhookUtilTest {

    private static final String URL = "https://example.invalid/banking/v2/webhooks/pix-pagamento";
    private static final String SCOPE = "webhook-banking.write";

    private MockedStatic<HttpUtils> httpUtils;
    private Config config;

    @Before
    public void setUp() {
        config = TestFixtures.config();
        httpUtils = mockStatic(HttpUtils.class);
    }

    @After
    public void tearDown() {
        httpUtils.close();
        TestStateReset.resetAll();
    }

    @Test
    public void includeWebhookShouldPutSerializedRequest() throws SdkException {
        IncludeWebhookRequest request = IncludeWebhookRequest.builder().webhookUrl("https://hook.example/cb").build();

        WebhookUtil.includeWebhook(config, URL, request, SCOPE);

        httpUtils.verify(() -> HttpUtils.callPut(eq(config), eq(URL), eq(SCOPE), eq("Error including webhook"),
                argThatContains("\"webhookUrl\" : \"https://hook.example/cb\"")));
    }

    @Test
    public void includeWebhookShouldWrapSerializationFailure() {
        try (MockedConstruction<ObjectMapper> ignored = JsonFailure.failingSerialization()) {
            WebhookUtil.includeWebhook(config, URL, IncludeWebhookRequest.builder().build(), SCOPE);
            fail();
        } catch (SdkException e) {
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
            assertEquals(JsonFailure.MESSAGE, e.getError().getDetail());
            assertEquals(JsonFailure.MESSAGE, e.getMessage());
            httpUtils.verifyNoInteractions();
        }
    }

    @Test
    public void shouldBeInstantiable() {
        assertNotNull(new WebhookUtil());
    }

    @Test
    public void includeWebhookShouldPropagateHttpErrors() {
        httpUtils.when(() -> HttpUtils.callPut(any(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new ClientException("Error including webhook", Error.builder().title("nope").build()));
        try {
            WebhookUtil.includeWebhook(config, URL, IncludeWebhookRequest.builder().build(), SCOPE);
            fail();
        } catch (SdkException e) {
            assertEquals("nope", e.getError().getTitle());
        }
    }

    @Test
    public void retrieveWebhookShouldParseResponse() throws SdkException {
        httpUtils.when(() -> HttpUtils.callGet(config, URL, SCOPE, "Error retrieving webhook"))
                .thenReturn("{\"webhookUrl\":\"https://hook.example/cb\",\"criacao\":\"2024-01-01T00:00:00Z\"}");

        Webhook webhook = WebhookUtil.retrieveWebhook(config, URL, SCOPE);

        assertEquals("https://hook.example/cb", webhook.getWebhookUrl());
        assertEquals("2024-01-01T00:00:00Z", webhook.getCreationDate());
    }

    @Test
    public void retrieveWebhookShouldWrapMalformedJson() {
        httpUtils.when(() -> HttpUtils.callGet(any(), anyString(), anyString(), anyString())).thenReturn("not-json");
        try {
            WebhookUtil.retrieveWebhook(config, URL, SCOPE);
            fail();
        } catch (SdkException e) {
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
            assertTrue(e.getError().getDetail().contains("not-json"));
            assertEquals(e.getMessage(), e.getError().getDetail());
        }
    }

    static String argThatContains(String fragment) {
        return org.mockito.ArgumentMatchers.argThat(s -> s != null && s.contains(fragment));
    }
}
