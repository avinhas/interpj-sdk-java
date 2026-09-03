package inter.sdk.pix.webhooks;

import inter.sdk.commons.ClientTestSupport;
import inter.sdk.commons.models.Webhook;
import inter.sdk.pix.models.CallbackRetrieveFilter;
import inter.sdk.pix.models.PixCallbackPage;
import inter.sdk.pix.models.RetrieveCallbackResponse;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PixWebhookSdkTest extends ClientTestSupport {

    private static final String READ = "webhook.read";
    private static final String WRITE = "webhook.write";
    private static final String WEBHOOK = BASE + "/pix/v2/webhook";
    private static final String CALLBACKS = WEBHOOK + "/callbacks?dataHoraInicio=2024-01-01T00:00&dataHoraFim=2024-01-02T00:00";

    private final PixWebhookSdk sdk = new PixWebhookSdk();

    @Test
    public void shouldDeleteWebhook() throws Exception {
        sdk.deleteWebhook(config, "key1");
        verifyDelete(WEBHOOK + "/key1", WRITE, "Error deleting webhook");
    }

    @Test
    public void shouldIncludeWebhook() throws Exception {
        sdk.includeWebhook(config, "key1", "https://hook.example/cb");
        verifyPut(WEBHOOK + "/key1", WRITE, "Error including webhook", "\"webhookUrl\" : \"https://hook.example/cb\"");
    }

    @Test
    public void shouldRetrieveWebhook() throws Exception {
        onGet(WEBHOOK + "/key1", READ, "{\"webhookUrl\":\"https://hook.example/cb\"}");
        Webhook webhook = sdk.retrieveWebhook(config, "key1");
        assertEquals("https://hook.example/cb", webhook.getWebhookUrl());
        verifyGet(WEBHOOK + "/key1", READ, "Error retrieving webhook");
    }

    @Test
    public void shouldRetrieveCallbackPageWithFilter() throws Exception {
        String url = CALLBACKS + "&pagina=1&tamanhoPagina=5&txid=tx1";
        onGet(url, READ, "{\"totalPaginas\":1,\"data\":[{\"webhookUrl\":\"u\",\"httpStatus\":200}]}");

        PixCallbackPage page = sdk.retrieveCallbackPage(config, "2024-01-01T00:00", "2024-01-02T00:00", 1, 5,
                CallbackRetrieveFilter.builder().txid("tx1").build());

        assertEquals(Integer.valueOf(200), page.getData().get(0).getHttpStatus());
        verifyGet(url, READ, "Error retrieving callbacks");

        onGet(CALLBACKS + "&pagina=0", READ, "{\"totalPaginas\":1,\"data\":[]}");
        assertTrue(sdk.retrieveCallbackPage(config, "2024-01-01T00:00", "2024-01-02T00:00", 0, null,
                CallbackRetrieveFilter.builder().build()).getData().isEmpty());
    }

    @Test
    public void shouldWalkAllCallbackPages() throws Exception {
        onGet(CALLBACKS + "&pagina=0", READ, "{\"totalPaginas\":2,\"data\":[{\"webhookUrl\":\"a\"}]}");
        onGet(CALLBACKS + "&pagina=1", READ, "{\"totalPaginas\":2,\"data\":[{\"webhookUrl\":\"b\"}]}");

        List<RetrieveCallbackResponse> all = sdk.retrieveCallbackInRange(config, "2024-01-01T00:00", "2024-01-02T00:00", null);

        assertEquals(2, all.size());
        assertEquals("b", all.get(1).getWebhookUrl());
    }

    @Test
    public void shouldWrapMalformedCallbackPage() {
        onGet(CALLBACKS + "&pagina=0", READ, NOT_JSON);
        assertWrapsParseFailure(() -> sdk.retrieveCallbackInRange(config, "2024-01-01T00:00", "2024-01-02T00:00", null));
    }
}
