package inter.sdk.pix.immediatebillings;

import inter.sdk.commons.ClientTestSupport;
import inter.sdk.pix.enums.BillingStatus;
import inter.sdk.pix.models.BillingPage;
import inter.sdk.pix.models.DetailedImmediatePixBilling;
import inter.sdk.pix.models.GeneratedImmediateBilling;
import inter.sdk.pix.models.PixBilling;
import inter.sdk.pix.models.RetrieveImmediateBillingsFilter;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImmediateBillingClientTest extends ClientTestSupport {

    private static final String READ = "cob.read";
    private static final String WRITE = "cob.write";
    private static final String URL = BASE + "/pix/v2/cob";
    private static final String RANGE = "?inicio=2024-01-01&fim=2024-01-31";
    private static final String TWO_PAGES = "{\"parametros\":{\"paginacao\":{\"quantidadeDePaginas\":2}},\"cobs\":[{\"txid\":\"%s\"}]}";

    private final ImmediateBillingClient client = new ImmediateBillingClient();

    @Test
    public void shouldPostWhenTxidIsAbsent() throws Exception {
        onPost(URL, WRITE, "{\"txid\":\"generated\",\"status\":\"ATIVA\"}");

        GeneratedImmediateBilling result = client.includeImmediateBilling(config, PixBilling.builder().key("k").build());

        assertEquals("generated", result.getTxid());
        verifyPost(URL, WRITE, "Error including immediate billing", "\"chave\" : \"k\"");
    }

    @Test
    public void shouldPutWhenTxidIsPresent() throws Exception {
        onPut(URL + "/tx1", WRITE, "{\"txid\":\"tx1\",\"revisao\":0}");

        GeneratedImmediateBilling result = client.includeImmediateBilling(config, PixBilling.builder().txid("tx1").key("k").build());

        assertEquals("tx1", result.getTxid());
        verifyPut(URL + "/tx1", WRITE, "Error including immediate billing", "\"txid\" : \"tx1\"");
    }

    @Test
    public void shouldWrapMalformedInclude() {
        onPost(URL, WRITE, NOT_JSON);
        assertWrapsParseFailure(() -> client.includeImmediateBilling(config, PixBilling.builder().build()));
    }

    @Test
    public void shouldRetrieveImmediateBilling() throws Exception {
        onGet(URL + "/tx1", READ, "{\"txid\":\"tx1\",\"status\":\"ATIVA\",\"pixCopiaECola\":\"copy\"}");

        DetailedImmediatePixBilling billing = client.retrieveImmediateBilling(config, "tx1");

        assertEquals("copy", billing.getPixCopyAndPaste());
        verifyGet(URL + "/tx1", READ, "Error retrieving immediate billing");
    }

    @Test
    public void shouldWrapMalformedRetrieve() {
        onGet(URL + "/tx1", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveImmediateBilling(config, "tx1"));
    }

    @Test
    public void shouldRetrievePageWithAllFilters() throws Exception {
        String url = URL + RANGE + "&paginacao.paginaAtual=1&paginacao.itensPorPagina=10&cpf=123&cnpj=456&locationPresente=true&status=ATIVA";
        onGet(url, READ, String.format(TWO_PAGES, "a"));

        BillingPage page = client.retrieveImmediateBillingPage(config, "2024-01-01", "2024-01-31", 1, 10,
                RetrieveImmediateBillingsFilter.builder().cpf("123").cnpj("456").locationPresente(true).status(BillingStatus.ATIVA).build());

        assertEquals(2, page.getTotalPages());
        assertEquals("a", page.getBillings().get(0).getTxid());
        verifyGet(url, READ, "Error retrieving list of immediate billings");
    }

    @Test
    public void shouldRetrievePageWithPartialFilter() throws Exception {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0&cnpj=456", READ, "{\"cobs\":[]}");
        assertTrue(client.retrieveImmediateBillingPage(config, "2024-01-01", "2024-01-31", 0, null,
                RetrieveImmediateBillingsFilter.builder().cnpj("456").build()).getBillings().isEmpty());

        onGet(URL + RANGE + "&paginacao.paginaAtual=0&cpf=1&locationPresente=false", READ, "{\"parametros\":{},\"cobs\":[]}");
        assertEquals(0, client.retrieveImmediateBillingPage(config, "2024-01-01", "2024-01-31", 0, null,
                RetrieveImmediateBillingsFilter.builder().cpf("1").locationPresente(false).build()).getTotalPages());
    }

    @Test
    public void shouldWalkAllPages() throws Exception {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, String.format(TWO_PAGES, "a"));
        onGet(URL + RANGE + "&paginacao.paginaAtual=1", READ, String.format(TWO_PAGES, "b"));

        List<DetailedImmediatePixBilling> all = client.retrieveImmediateBillingInRange(config, "2024-01-01", "2024-01-31", null);

        assertEquals(2, all.size());
        assertEquals("b", all.get(1).getTxid());
    }

    @Test
    public void shouldWrapMalformedPage() {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveImmediateBillingInRange(config, "2024-01-01", "2024-01-31", null));
    }

    @Test
    public void shouldReviewImmediateBilling() throws Exception {
        onPatch(URL + "/tx1", WRITE, "{\"txid\":\"tx1\",\"revisao\":1}");

        GeneratedImmediateBilling result = client.reviewImmediateBilling(config, PixBilling.builder().txid("tx1").key("k2").build());

        assertEquals(Integer.valueOf(1), result.getRevision());
        verifyPatch(URL + "/tx1", WRITE, "Error reviewing immediate billing", "\"chave\" : \"k2\"");
    }

    @Test
    public void shouldWrapMalformedReview() {
        onPatch(URL + "/tx1", WRITE, NOT_JSON);
        assertWrapsParseFailure(() -> client.reviewImmediateBilling(config, PixBilling.builder().txid("tx1").build()));
    }
}
