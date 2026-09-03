package inter.sdk.banking.webhooks;

import inter.sdk.banking.models.CallbackPage;
import inter.sdk.banking.models.CallbackRetrieveFilter;
import inter.sdk.banking.models.RetrieveCallbackResponse;
import inter.sdk.commons.ClientTestSupport;
import inter.sdk.commons.models.Webhook;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BankingWebhookClientTest extends ClientTestSupport {

    private static final String READ = "webhook-banking.read";
    private static final String WRITE = "webhook-banking.write";
    private static final String WEBHOOK = BASE + "/banking/v2/webhooks/pix-pagamento";
    private static final String CALLBACKS = WEBHOOK + "/callbacks?dataHoraInicio=2024-01-01T00:00&dataHoraFim=2024-01-02T00:00";

    private final BankingWebhookClient client = new BankingWebhookClient();

    @Test
    public void shouldDeleteWebhook() throws Exception {
        client.deleteWebhook(config, "pix-pagamento");
        verifyDelete(WEBHOOK, WRITE, "Error deleting webhook");
    }

    @Test
    public void shouldIncludeWebhookThroughWebhookUtil() throws Exception {
        client.includeWebhook(config, "pix-pagamento", "https://hook.example/cb");
        verifyPut(WEBHOOK, WRITE, "Error including webhook", "\"webhookUrl\" : \"https://hook.example/cb\"");
    }

    @Test
    public void shouldRetrieveWebhook() throws Exception {
        onGet(WEBHOOK, READ, "{\"webhookUrl\":\"https://hook.example/cb\",\"criacao\":\"2024\"}");

        Webhook webhook = client.retrieveWebhook(config, "pix-pagamento");

        assertEquals("https://hook.example/cb", webhook.getWebhookUrl());
        verifyGet(WEBHOOK, READ, "Error retrieving webhook");
    }

    @Test
    public void shouldRetrieveCallbackPageWithFilters() throws Exception {
        String url = CALLBACKS + "&pagina=1&tamanhoPagina=20&codigoTransacao=T1&endToEnd=E1";
        onGet(url, READ, "{\"totalPaginas\":2,\"data\":[{\"webhookUrl\":\"u\",\"sucesso\":true,\"httpStatus\":200}]}");
        CallbackRetrieveFilter filter = CallbackRetrieveFilter.builder().transactionCode("T1").endToEndId("E1").build();

        CallbackPage page = client.retrieveCallbackPage(config, "pix-pagamento", "2024-01-01T00:00", "2024-01-02T00:00", 1, 20, filter);

        assertEquals(Integer.valueOf(2), page.getTotalPages());
        assertEquals(Integer.valueOf(200), page.getData().get(0).getHttpStatus());
        verifyGet(url, READ, "Error retrieving callbacks");
    }

    @Test
    public void shouldRetrieveCallbackPageWithPartialFilter() throws Exception {
        onGet(CALLBACKS + "&pagina=0&endToEnd=E1", READ, "{\"totalPaginas\":1,\"data\":[]}");
        CallbackPage page = client.retrieveCallbackPage(config, "pix-pagamento", "2024-01-01T00:00", "2024-01-02T00:00", 0, null,
                CallbackRetrieveFilter.builder().endToEndId("E1").build());
        assertTrue(page.getData().isEmpty());

        onGet(CALLBACKS + "&pagina=0&codigoTransacao=T1", READ, "{\"totalPaginas\":1,\"data\":[]}");
        client.retrieveCallbackPage(config, "pix-pagamento", "2024-01-01T00:00", "2024-01-02T00:00", 0, null,
                CallbackRetrieveFilter.builder().transactionCode("T1").build());
        verifyGet(CALLBACKS + "&pagina=0&codigoTransacao=T1", READ, "Error retrieving callbacks");
    }

    @Test
    public void shouldWalkAllCallbackPages() throws Exception {
        onGet(CALLBACKS + "&pagina=0", READ, "{\"totalPaginas\":2,\"data\":[{\"webhookUrl\":\"a\"}]}");
        onGet(CALLBACKS + "&pagina=1", READ, "{\"totalPaginas\":2,\"data\":[{\"webhookUrl\":\"b\"}]}");

        List<RetrieveCallbackResponse> all = client.retrieveCallbacksInRange(config, "pix-pagamento", "2024-01-01T00:00", "2024-01-02T00:00", null);

        assertEquals(2, all.size());
        assertEquals("b", all.get(1).getWebhookUrl());
    }

    @Test
    public void shouldWrapMalformedCallbackPage() {
        onGet(CALLBACKS + "&pagina=0", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveCallbacksInRange(config, "pix-pagamento", "2024-01-01T00:00", "2024-01-02T00:00", null));
    }
}
