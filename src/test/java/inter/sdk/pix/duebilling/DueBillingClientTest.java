package inter.sdk.pix.duebilling;

import inter.sdk.commons.ClientTestSupport;
import inter.sdk.pix.enums.BillingStatus;
import inter.sdk.pix.models.DetailedDuePixBilling;
import inter.sdk.pix.models.DueBilling;
import inter.sdk.pix.models.DueBillingPage;
import inter.sdk.pix.models.GeneratedDueBilling;
import inter.sdk.pix.models.RetrieveDueBillingFilter;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DueBillingClientTest extends ClientTestSupport {

    private static final String READ = "cobv.read";
    private static final String WRITE = "cobv.write";
    private static final String URL = BASE + "/pix/v2/cobv";
    private static final String RANGE = "?inicio=2024-01-01&fim=2024-01-31";
    private static final String TWO_PAGES = "{\"parametros\":{\"paginacao\":{\"paginaAtual\":0,\"quantidadeDePaginas\":2}},\"cobs\":[{\"txid\":\"%s\"}]}";

    private final DueBillingClient client = new DueBillingClient();

    @Test
    public void shouldIncludeDueBilling() throws Exception {
        onPut(URL + "/tx1", WRITE, "{\"txid\":\"tx1\",\"status\":\"ATIVA\",\"revisao\":0}");

        GeneratedDueBilling result = client.includeDueBilling(config, "tx1",
                DueBilling.builder().key("pix-key").payerRequest("pay me").build());

        assertEquals("tx1", result.getTxid());
        assertEquals("ATIVA", result.getStatus());
        verifyPut(URL + "/tx1", WRITE, "Error including due billing", "\"chave\" : \"pix-key\"", "\"solicitacaoPagador\" : \"pay me\"");
    }

    @Test
    public void shouldWrapMalformedInclude() {
        onPut(URL + "/tx1", WRITE, NOT_JSON);
        assertWrapsParseFailure(() -> client.includeDueBilling(config, "tx1", DueBilling.builder().build()));
    }

    @Test
    public void shouldRetrieveDueBilling() throws Exception {
        onGet(URL + "/tx1", READ, "{\"txid\":\"tx1\",\"status\":\"ATIVA\",\"pixCopiaECola\":\"copy\"}");

        DetailedDuePixBilling billing = client.retrieveDueBilling(config, "tx1");

        assertEquals("copy", billing.getPixCopyAndPaste());
        assertEquals(BillingStatus.ATIVA, billing.getStatus());
        verifyGet(URL + "/tx1", READ, "Error retrieving due billing");
    }

    @Test
    public void shouldWrapMalformedRetrieve() {
        onGet(URL + "/tx1", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveDueBilling(config, "tx1"));
    }

    @Test
    public void shouldRetrievePageWithAllFilters() throws Exception {
        String url = URL + RANGE + "&paginacao.paginaAtual=1&paginacao.itensPorPagina=10&cpf=123&cnpj=456&locationPresente=true&status=ATIVA";
        onGet(url, READ, String.format(TWO_PAGES, "a"));

        DueBillingPage page = client.retrieveDueBillingPage(config, "2024-01-01", "2024-01-31", 1, 10,
                RetrieveDueBillingFilter.builder().cpf("123").cnpj("456").locationPresent(true).status(BillingStatus.ATIVA).build());

        assertEquals(2, page.getTotalPages());
        assertEquals("a", page.getDueBillings().get(0).getTxid());
        verifyGet(url, READ, "Error retrieving due billing");
    }

    @Test
    public void shouldRetrievePageWithPartialFilter() throws Exception {
        String url = URL + RANGE + "&paginacao.paginaAtual=0&cnpj=456";
        onGet(url, READ, "{\"cobs\":[]}");
        DueBillingPage page = client.retrieveDueBillingPage(config, "2024-01-01", "2024-01-31", 0, null,
                RetrieveDueBillingFilter.builder().cnpj("456").build());
        assertTrue(page.getDueBillings().isEmpty());
        assertEquals(0, page.getTotalPages());

        onGet(URL + RANGE + "&paginacao.paginaAtual=0&cpf=1&locationPresente=false", READ, "{\"parametros\":{},\"cobs\":[]}");
        assertEquals(0, client.retrieveDueBillingPage(config, "2024-01-01", "2024-01-31", 0, null,
                RetrieveDueBillingFilter.builder().cpf("1").locationPresent(false).build()).getTotalPages());
    }

    @Test
    public void shouldWalkAllPages() throws Exception {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, String.format(TWO_PAGES, "a"));
        onGet(URL + RANGE + "&paginacao.paginaAtual=1", READ, String.format(TWO_PAGES, "b"));

        List<DetailedDuePixBilling> all = client.retrieveDuePixBillingInRange(config, "2024-01-01", "2024-01-31", null);

        assertEquals(2, all.size());
        assertEquals("b", all.get(1).getTxid());
    }

    @Test
    public void shouldWrapMalformedPage() {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveDuePixBillingInRange(config, "2024-01-01", "2024-01-31", null));
    }

    @Test
    public void shouldReviewDueBilling() throws Exception {
        onPatch(URL + "/tx1", WRITE, "{\"txid\":\"tx1\",\"revisao\":1}");

        GeneratedDueBilling result = client.reviewDueBilling(config, "tx1", DueBilling.builder().key("k2").build());

        assertEquals(Integer.valueOf(1), result.getRevision());
        verifyPatch(URL + "/tx1", WRITE, "Error retrieving due billing", "\"chave\" : \"k2\"");
    }

    @Test
    public void shouldWrapMalformedReview() {
        onPatch(URL + "/tx1", WRITE, NOT_JSON);
        assertWrapsParseFailure(() -> client.reviewDueBilling(config, "tx1", DueBilling.builder().build()));
    }
}
