package inter.sdk.pix.pix;

import inter.sdk.commons.ClientTestSupport;
import inter.sdk.pix.models.DetailedDevolution;
import inter.sdk.pix.models.DevolutionRequestBody;
import inter.sdk.pix.models.Pix;
import inter.sdk.pix.models.PixPage;
import inter.sdk.pix.models.RetrievedPixFilter;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PixClientTest extends ClientTestSupport {

    private static final String READ = "pix.read";
    private static final String WRITE = "pix.write";
    private static final String URL = BASE + "/pix/v2/pix";
    private static final String RANGE = "?inicio=2024-01-01&fim=2024-01-31";
    private static final String TWO_PAGES = "{\"parametros\":{\"paginacao\":{\"quantidadeDePaginas\":2}},\"pix\":[{\"endToEndId\":\"%s\"}]}";

    private final PixClient client = new PixClient();

    @Test
    public void shouldRequestDevolution() throws Exception {
        onPut(URL + "/E1/devolucao/D1", WRITE, "{\"id\":\"D1\",\"status\":\"EM_PROCESSAMENTO\",\"valor\":\"5.00\"}");

        DetailedDevolution result = client.requestDevolution(config, "E1", "D1",
                DevolutionRequestBody.builder().value("5.00").description("refund").build());

        assertEquals("D1", result.getId());
        assertEquals("5.00", result.getValue());
        verifyPut(URL + "/E1/devolucao/D1", WRITE, "Error requesting devolution", "\"valor\" : \"5.00\"", "\"descricao\" : \"refund\"");
    }

    @Test
    public void shouldWrapMalformedDevolutionRequest() {
        onPut(URL + "/E1/devolucao/D1", WRITE, NOT_JSON);
        assertWrapsParseFailure(() -> client.requestDevolution(config, "E1", "D1", DevolutionRequestBody.builder().build()));
    }

    @Test
    public void shouldRetrieveDevolution() throws Exception {
        onGet(URL + "/E1/devolucao/D1", READ, "{\"id\":\"D1\",\"rtrId\":\"R\"}");

        DetailedDevolution result = client.retrieveDevolution(config, "E1", "D1");

        assertEquals("R", result.getRtrId());
        verifyGet(URL + "/E1/devolucao/D1", READ, "Error retrieving devolution");
    }

    @Test
    public void shouldWrapMalformedDevolution() {
        onGet(URL + "/E1/devolucao/D1", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveDevolution(config, "E1", "D1"));
    }

    @Test
    public void shouldRetrievePixTransaction() throws Exception {
        onGet(URL + "/E1", READ, "{\"endToEndId\":\"E1\",\"txid\":\"tx\",\"valor\":\"1.00\"}");

        Pix pix = client.retrievePixTransaction(config, "E1");

        assertEquals("tx", pix.getTxid());
        verifyGet(URL + "/E1", READ, "Error retrieving pix");
    }

    @Test
    public void shouldWrapMalformedPixTransaction() {
        onGet(URL + "/E1", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrievePixTransaction(config, "E1"));
    }

    @Test
    public void shouldRetrievePageWithAllFilters() throws Exception {
        String url = URL + RANGE + "&paginacao.paginaAtual=1&paginacao.itensPorPagina=10&txId=tx&txIdPresente=true&devolucaoPresente=false&cpf=123&cnpj=456";
        onGet(url, READ, String.format(TWO_PAGES, "a"));

        PixPage page = client.retrievePixPage(config, "2024-01-01", "2024-01-31", 1, 10, RetrievedPixFilter.builder()
                .txId("tx").txIdPresent(true).devolutionPresent(false).cpf("123").cnpj("456").build());

        assertEquals(2, page.getTotalPages());
        assertEquals("a", page.getPixList().get(0).getEndToEndId());
        verifyGet(url, READ, "Error retrieving pix");
    }

    @Test
    public void shouldRetrievePageWithPartialFilter() throws Exception {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0&cnpj=456", READ, "{\"pix\":[]}");
        assertTrue(client.retrievePixPage(config, "2024-01-01", "2024-01-31", 0, null,
                RetrievedPixFilter.builder().cnpj("456").build()).getPixList().isEmpty());

        onGet(URL + RANGE + "&paginacao.paginaAtual=0&txIdPresente=false&cpf=1", READ, "{\"parametros\":{},\"pix\":[]}");
        assertEquals(0, client.retrievePixPage(config, "2024-01-01", "2024-01-31", 0, null,
                RetrievedPixFilter.builder().txIdPresent(false).cpf("1").build()).getTotalPages());

        onGet(URL + RANGE + "&paginacao.paginaAtual=0&txId=t&devolucaoPresente=true", READ, "{\"pix\":[]}");
        client.retrievePixPage(config, "2024-01-01", "2024-01-31", 0, null,
                RetrievedPixFilter.builder().txId("t").devolutionPresent(true).build());
        verifyGet(URL + RANGE + "&paginacao.paginaAtual=0&txId=t&devolucaoPresente=true", READ, "Error retrieving pix");
    }

    @Test
    public void shouldWalkAllPages() throws Exception {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, String.format(TWO_PAGES, "a"));
        onGet(URL + RANGE + "&paginacao.paginaAtual=1", READ, String.format(TWO_PAGES, "b"));

        List<Pix> all = client.retrievePixInRange(config, "2024-01-01", "2024-01-31", null);

        assertEquals(2, all.size());
        assertEquals("b", all.get(1).getEndToEndId());
    }

    @Test
    public void shouldWrapMalformedPage() {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrievePixInRange(config, "2024-01-01", "2024-01-31", null));
    }
}
