package inter.sdk.pix.duebillingbatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import inter.sdk.commons.ClientTestSupport;
import inter.sdk.commons.JsonFailure;
import inter.sdk.commons.exceptions.SdkException;
import inter.sdk.pix.models.DueBilling;
import inter.sdk.pix.models.DueBillingBatch;
import inter.sdk.pix.models.DueBillingBatchPage;
import inter.sdk.pix.models.DueBillingBatchSummary;
import inter.sdk.pix.models.IncludeDueBillingBatchRequest;
import org.junit.Test;
import org.mockito.MockedConstruction;

import java.util.Collections;
import java.util.List;

import static inter.sdk.commons.structures.Constants.CERTIFICATE_EXCEPTION_MESSAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DueBillingBatchClientTest extends ClientTestSupport {

    private static final String READ = "lotecobv.read";
    private static final String WRITE = "lotecobv.write";
    private static final String URL = BASE + "/pix/v2/lotecobv";
    private static final String RANGE = "?inicio=2024-01-01&fim=2024-01-31";
    private static final String TWO_PAGES = "{\"parametros\":{\"paginacao\":{\"quantidadeDePaginas\":2}},\"lotes\":[{\"id\":\"%s\"}]}";

    private final DueBillingBatchClient client = new DueBillingBatchClient();

    private static IncludeDueBillingBatchRequest request() {
        return IncludeDueBillingBatchRequest.builder().description("batch")
                .dueBillings(Collections.singletonList(DueBilling.builder().txid("tx1").build())).build();
    }

    @Test
    public void shouldIncludeBatch() throws Exception {
        client.includeDueBillingBatch(config, "B1", request());
        verifyPut(URL + "/B1", WRITE, "Error including due billing in batch", "\"descricao\" : \"batch\"", "\"txid\" : \"tx1\"");
    }

    @Test
    public void shouldWrapSerializationFailureOnInclude() {
        try (MockedConstruction<ObjectMapper> ignored = JsonFailure.failingSerialization()) {
            client.includeDueBillingBatch(config, "B1", request());
            fail();
        } catch (SdkException e) {
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
            assertEquals(JsonFailure.MESSAGE, e.getError().getDetail());
            http.verifyNoInteractions();
        }
    }

    @Test
    public void shouldWrapSerializationFailureOnReview() {
        try (MockedConstruction<ObjectMapper> ignored = JsonFailure.failingSerialization()) {
            client.reviewDueBillingBatch(config, "B1", request());
            fail();
        } catch (SdkException e) {
            assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
            assertEquals(JsonFailure.MESSAGE, e.getError().getDetail());
            http.verifyNoInteractions();
        }
    }

    @Test
    public void shouldRetrieveBatch() throws Exception {
        onGet(URL + "/B1", READ, "{\"id\":\"B1\",\"descricao\":\"d\",\"cobsv\":[{\"txid\":\"tx1\"}]}");

        DueBillingBatch batch = client.retrieveDueBillingBatch(config, "B1");

        assertEquals("B1", batch.getId());
        assertEquals(1, batch.getDueBillingEntities().size());
        verifyGet(URL + "/B1", READ, "Error retrieving due billing batch");
    }

    @Test
    public void shouldWrapMalformedBatch() {
        onGet(URL + "/B1", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveDueBillingBatch(config, "B1"));
    }

    @Test
    public void shouldRetrievePageWithPageSize() throws Exception {
        String url = URL + RANGE + "&paginacao.paginaAtual=1&paginacao.itensPorPagina=5";
        onGet(url, READ, String.format(TWO_PAGES, "a"));

        DueBillingBatchPage page = client.retrieveDueBillingBatchPage(config, "2024-01-01", "2024-01-31", 1, 5);

        assertEquals(2, page.getTotalPages());
        assertEquals("a", page.getBatches().get(0).getId());
        verifyGet(url, READ, "Error retrieving due billing batch");
    }

    @Test
    public void shouldWalkAllPages() throws Exception {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, String.format(TWO_PAGES, "a"));
        onGet(URL + RANGE + "&paginacao.paginaAtual=1", READ, String.format(TWO_PAGES, "b"));

        List<DueBillingBatch> all = client.retrieveDueBillingBatchInRange(config, "2024-01-01", "2024-01-31");

        assertEquals(2, all.size());
        assertEquals("b", all.get(1).getId());
    }

    @Test
    public void shouldWrapMalformedPage() {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveDueBillingBatchInRange(config, "2024-01-01", "2024-01-31"));
    }

    @Test
    public void shouldReviewBatch() throws Exception {
        client.reviewDueBillingBatch(config, "B1", request());
        verifyPatch(URL + "/B1", WRITE, "Error reviewing due billing in batch", "\"descricao\" : \"batch\"");
    }

    @Test
    public void shouldRetrieveSummary() throws Exception {
        onGet(URL + "/B1/sumario", READ, "{\"statusProcessamento\":\"OK\",\"totalCobrancas\":3,\"totalCobrancasCriadas\":2,\"totalCobrancasNegadas\":1}");

        DueBillingBatchSummary summary = client.retrieveDueBillingBatchSummary(config, "B1");

        assertEquals(Integer.valueOf(3), summary.getTotalBilling());
        assertEquals(Integer.valueOf(1), summary.getTotalBillingDenied());
        verifyGet(URL + "/B1/sumario", READ, "Error retrieving due billing batch summary");
    }

    @Test
    public void shouldWrapMalformedSummary() {
        onGet(URL + "/B1/sumario", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveDueBillingBatchSummary(config, "B1"));
    }

    @Test
    public void shouldRetrieveBatchBySituation() throws Exception {
        onGet(URL + "/B1/situacao/CRIADA", READ, "{\"id\":\"B1\"}");

        DueBillingBatch batch = client.retrieveDueBillingBatchBySituation(config, "B1", "CRIADA");

        assertEquals("B1", batch.getId());
        verifyGet(URL + "/B1/situacao/CRIADA", READ, "Error retrieving due billing batch by situation");
    }

    @Test
    public void shouldWrapMalformedBatchBySituation() {
        onGet(URL + "/B1/situacao/CRIADA", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveDueBillingBatchBySituation(config, "B1", "CRIADA"));
    }
}
