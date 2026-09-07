package inter.sdk.billing.webhooks;

import inter.sdk.billing.models.BillingCallbackPage;
import inter.sdk.billing.models.BillingRetrieveCallbackResponse;
import inter.sdk.billing.models.BillingRetrieveCallbacksFilter;
import inter.sdk.commons.ClientTestSupport;
import inter.sdk.commons.models.Webhook;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BillingWebhookClientTest extends ClientTestSupport {

    private static final String READ = "boleto-cobranca.read";
    private static final String WRITE = "boleto-cobranca.write";
    private static final String WEBHOOK = BASE + "/cobranca/v3/cobrancas/webhook";
    private static final String CALLBACKS = WEBHOOK + "/callbacks?dataHoraInicio=2024-01-01T00:00&dataHoraFim=2024-01-02T00:00";

    private final BillingWebhookClient client = new BillingWebhookClient();

    @Test
    public void shouldDeleteWebhook() throws Exception {
        client.deleteWebhook(config);
        verifyDelete(WEBHOOK, WRITE, "Error deleting webhook");
    }

    @Test
    public void shouldIncludeWebhook() throws Exception {
        client.includeWebhook(config, "https://hook.example/cb");
        verifyPut(WEBHOOK, WRITE, "Error including webhook", "\"webhookUrl\" : \"https://hook.example/cb\"");
    }

    @Test
    public void shouldRetrieveWebhook() throws Exception {
        onGet(WEBHOOK, READ, "{\"webhookUrl\":\"https://hook.example/cb\"}");
        Webhook webhook = client.retrieveWebhook(config);
        assertEquals("https://hook.example/cb", webhook.getWebhookUrl());
        verifyGet(WEBHOOK, READ, "Error retrieving webhook");
    }

    @Test
    public void shouldRetrieveCallbackPageWithFilter() throws Exception {
        String url = CALLBACKS + "&pagina=1&itensPorPagina=5&codigoSolicitacao=R1";
        onGet(url, READ, "{\"totalPaginas\":1,\"data\":[{\"webhookUrl\":\"u\",\"httpStatus\":200}]}");

        BillingCallbackPage page = client.retrieveCallbackPage(config, "2024-01-01T00:00", "2024-01-02T00:00", 1, 5,
                BillingRetrieveCallbacksFilter.builder().requestCode("R1").build());

        assertEquals(Integer.valueOf(200), page.getCallbacks().get(0).getHttpStatus());
        verifyGet(url, READ, "Error retrieving callbacks");

        onGet(CALLBACKS + "&pagina=0", READ, "{\"totalPaginas\":1,\"data\":[]}");
        assertTrue(client.retrieveCallbackPage(config, "2024-01-01T00:00", "2024-01-02T00:00", 0, null,
                BillingRetrieveCallbacksFilter.builder().build()).getCallbacks().isEmpty());
    }

    @Test
    public void shouldWalkAllCallbackPages() throws Exception {
        onGet(CALLBACKS + "&pagina=0", READ, "{\"totalPaginas\":2,\"data\":[{\"webhookUrl\":\"a\"}]}");
        onGet(CALLBACKS + "&pagina=1", READ, "{\"totalPaginas\":2,\"data\":[{\"webhookUrl\":\"b\"}]}");

        List<BillingRetrieveCallbackResponse> all = client.retrieveCallbacksInRange(config, "2024-01-01T00:00", "2024-01-02T00:00", null);

        assertEquals(2, all.size());
        assertEquals("b", all.get(1).getWebhookUrl());
    }

    @Test
    public void shouldWrapMalformedCallbackPage() {
        onGet(CALLBACKS + "&pagina=0", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveCallbacksInRange(config, "2024-01-01T00:00", "2024-01-02T00:00", null));
    }
}
