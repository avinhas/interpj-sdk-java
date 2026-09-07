package inter.sdk.billing.billing;

import inter.sdk.billing.enums.BillingDateType;
import inter.sdk.billing.enums.BillingSituation;
import inter.sdk.billing.enums.BillingType;
import inter.sdk.billing.enums.OrderBy;
import inter.sdk.billing.enums.OrderType;
import inter.sdk.billing.models.BillingIssueRequest;
import inter.sdk.billing.models.BillingIssueResponse;
import inter.sdk.billing.models.BillingPage;
import inter.sdk.billing.models.BillingRetrievalFilter;
import inter.sdk.billing.models.RetrievedBilling;
import inter.sdk.billing.models.Sorting;
import inter.sdk.billing.models.Summary;
import com.fasterxml.jackson.databind.ObjectMapper;
import inter.sdk.commons.ClientTestSupport;
import inter.sdk.commons.JsonFailure;
import inter.sdk.commons.exceptions.SdkException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedConstruction;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

import static inter.sdk.commons.structures.Constants.CERTIFICATE_EXCEPTION_MESSAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

public class BillingClientTest extends ClientTestSupport {

    private static final String READ = "boleto-cobranca.read";
    private static final String WRITE = "boleto-cobranca.write";
    private static final String URL = BASE + "/cobranca/v3/cobrancas";
    private static final String RANGE = "?dataInicial=2024-01-01&dataFinal=2024-01-31";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final BillingClient client = new BillingClient();

    @Test
    public void shouldCancelBilling() throws Exception {
        client.cancelBilling(config, "R1", "duplicate");
        verifyPost(URL + "/R1/cancelar", WRITE, "Error canceling billing", "\"motivoCancelamento\" : \"duplicate\"");
    }

    @Test
    public void shouldWrapSerializationFailureOnCancel() {
        try (MockedConstruction<ObjectMapper> ignored = JsonFailure.failingSerialization()) {
            client.cancelBilling(config, "R1", "duplicate");
            fail();
        } catch (SdkException e) {
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
            assertEquals(JsonFailure.MESSAGE, e.getError().getDetail());
            http.verifyNoInteractions();
        }
    }

    @Test
    public void shouldIssueBilling() throws Exception {
        onPost(URL, WRITE, "{\"codigoSolicitacao\":\"R1\"}");
        BillingIssueRequest request = BillingIssueRequest.builder()
                .yourNumber("123").nominalValue(new BigDecimal("99.90")).dueDate("2024-02-01").build();

        BillingIssueResponse response = client.issueBilling(config, request);

        assertEquals("R1", response.getRequestCode());
        verifyPost(URL, WRITE, "Error issuing billing", "\"seuNumero\" : \"123\"", "\"valorNominal\" : 99.90", "\"dataVencimento\" : \"2024-02-01\"");
    }

    @Test
    public void shouldWrapMalformedIssueResponse() {
        onPost(URL, WRITE, NOT_JSON);
        assertWrapsParseFailure(() -> client.issueBilling(config, BillingIssueRequest.builder().build()));
    }

    @Test
    public void shouldRetrieveBilling() throws Exception {
        onGet(URL + "/R1", READ, "{\"cobranca\":{\"seuNumero\":\"123\"},\"boleto\":{\"nossoNumero\":\"n\"},\"pix\":{\"txid\":\"t\"}}");

        RetrievedBilling billing = client.retrieveBilling(config, "R1");

        assertEquals("123", billing.getBilling().getYourNumber());
        assertEquals("t", billing.getPix().getTransactionId());
        verifyGet(URL + "/R1", READ, "Error retrieving billing");
    }

    @Test
    public void shouldWrapMalformedBilling() {
        onGet(URL + "/R1", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveBilling(config, "R1"));
    }

    @Test
    public void shouldRetrieveBillingPageWithAllFiltersAndSorting() throws Exception {
        String url = URL + RANGE + "&paginacao.paginaAtual=1&paginacao.itensPorPagina=10"
                + "&filtrarDataPor=VENCIMENTO&situacao=RECEBIDO&pessoaPagadora=John&cpfCnpjPessoaPagadora=123&seuNumero=S1&tipoCobranca=SIMPLES"
                + "&ordenarPor=PESSOA_PAGADORA&tipoOrdenacao=DESC";
        onGet(url, READ, "{\"totalPaginas\":1,\"cobrancas\":[{\"cobranca\":{\"seuNumero\":\"S1\"}}]}");
        BillingRetrievalFilter filter = BillingRetrievalFilter.builder()
                .filterDateBy(BillingDateType.VENCIMENTO).situation(BillingSituation.RECEBIDO)
                .payer("John").payerCpfCnpj("123").yourNumber("S1").billingType(BillingType.SIMPLES).build();
        Sorting sort = Sorting.builder().orderBy(OrderBy.PESSOA_PAGADORA).sortType(OrderType.DESC).build();

        BillingPage page = client.retrieveBillingPage(config, "2024-01-01", "2024-01-31", 1, 10, filter, sort);

        assertEquals("S1", page.getBillings().get(0).getBilling().getYourNumber());
        verifyGet(url, READ, "Error retrieving billing collection");
    }

    @Test
    public void shouldHandlePartialFilterAndSort() throws Exception {
        String url = URL + RANGE + "&paginacao.paginaAtual=0&situacao=A_RECEBER&ordenarPor=TIPO_COBRANCA";
        onGet(url, READ, "{\"totalPaginas\":1,\"cobrancas\":[]}");
        client.retrieveBillingPage(config, "2024-01-01", "2024-01-31", 0, null,
                BillingRetrievalFilter.builder().situation(BillingSituation.A_RECEBER).build(),
                Sorting.builder().orderBy(OrderBy.TIPO_COBRANCA).build());
        verifyGet(url, READ, "Error retrieving billing collection");

        String url2 = URL + RANGE + "&paginacao.paginaAtual=0&tipoOrdenacao=ASC";
        onGet(url2, READ, "{\"totalPaginas\":1,\"cobrancas\":[]}");
        client.retrieveBillingPage(config, "2024-01-01", "2024-01-31", 0, null,
                BillingRetrievalFilter.builder().build(), Sorting.builder().sortType(OrderType.ASC).build());
        verifyGet(url2, READ, "Error retrieving billing collection");
    }

    @Test
    public void shouldWalkAllBillingPages() throws Exception {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, "{\"totalPaginas\":2,\"cobrancas\":[{\"cobranca\":{\"seuNumero\":\"a\"}}]}");
        onGet(URL + RANGE + "&paginacao.paginaAtual=1", READ, "{\"totalPaginas\":2,\"cobrancas\":[{\"cobranca\":{\"seuNumero\":\"b\"}}]}");

        List<RetrievedBilling> all = client.retrieveBillingsInRange(config, "2024-01-01", "2024-01-31", null, null);

        assertEquals(2, all.size());
        assertEquals("b", all.get(1).getBilling().getYourNumber());
    }

    @Test
    public void shouldWrapMalformedPage() {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveBillingsInRange(config, "2024-01-01", "2024-01-31", null, null));
    }

    @Test
    public void shouldWritePdf() throws Exception {
        String pdf = Base64.getEncoder().encodeToString("%PDF billing".getBytes(StandardCharsets.UTF_8));
        onGet(URL + "/R1/pdf", READ, "{\"pdf\":\"" + pdf + "\"}");
        File out = new File(tmp.getRoot(), "billing.pdf");

        client.retrieveBillingInPDF(config, "R1", out.getAbsolutePath());

        assertEquals("%PDF billing", new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
        verifyGet(URL + "/R1/pdf", READ, "Error retrieving billing pdf");
    }

    @Test
    public void shouldWrapPdfFailures() {
        onGet(URL + "/R1/pdf", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveBillingInPDF(config, "R1", "ignored.pdf"));

        onGet(URL + "/R1/pdf", READ, "{\"pdf\":\"AA==\"}");
        String unwritable = new File(tmp.getRoot(), "nope/x.pdf").getAbsolutePath();
        assertWrapsParseFailure(() -> client.retrieveBillingInPDF(config, "R1", unwritable));
    }

    @Test
    public void shouldRetrieveSummary() throws Exception {
        String url = URL + "/sumario" + RANGE + "&seuNumero=S1";
        onGet(url, READ, "[{\"situacao\":\"RECEBIDO\",\"quantidade\":2,\"valor\":20.5}]");

        Summary summary = client.retrieveBillingSummary(config, "2024-01-01", "2024-01-31",
                BillingRetrievalFilter.builder().yourNumber("S1").build());

        assertEquals(1, summary.size());
        assertEquals(Integer.valueOf(2), summary.get(0).getQuantity());
        verifyGet(url, READ, "Error retrieving billing summary");

        onGet(URL + "/sumario" + RANGE, READ, "[]");
        assertTrue(client.retrieveBillingSummary(config, "2024-01-01", "2024-01-31", null).isEmpty());
    }

    @Test
    public void shouldWrapMalformedSummary() {
        onGet(URL + "/sumario" + RANGE, READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveBillingSummary(config, "2024-01-01", "2024-01-31", null));
    }
}
