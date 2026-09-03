package inter.sdk.banking.pix;

import inter.sdk.banking.enums.PixStatus;
import inter.sdk.banking.models.IncludePixResponse;
import inter.sdk.banking.models.Key;
import inter.sdk.banking.models.Pix;
import inter.sdk.banking.models.RetrievePixResponse;
import inter.sdk.commons.ClientTestSupport;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BankingPixClientTest extends ClientTestSupport {

    private static final String URL = BASE + "/banking/v2/pix";
    private final BankingPixClient client = new BankingPixClient();

    @Test
    public void shouldIncludePix() throws Exception {
        onPost(URL, "pagamento-pix.write", "{\"tipoRetorno\":\"APROVACAO\",\"codigoSolicitacao\":\"S1\",\"dataPagamento\":\"2024-01-01\"}");
        Pix pix = Pix.builder().amount("10.00").description("lunch").recipient(new Key("abc@pix")).build();

        IncludePixResponse response = client.includePix(config, pix);

        assertEquals("APROVACAO", response.getReturnType());
        assertEquals("S1", response.getRequestCode());
        verifyPost(URL, "pagamento-pix.write", "Error including pix",
                "\"valor\" : \"10.00\"", "\"descricao\" : \"lunch\"", "\"tipo\" : \"CHAVE\"", "\"chave\" : \"abc@pix\"");
    }

    @Test
    public void shouldWrapMalformedIncludeResponse() {
        onPost(URL, "pagamento-pix.write", NOT_JSON);
        assertWrapsParseFailure(() -> client.includePix(config, Pix.builder().build()));
    }

    @Test
    public void shouldRetrievePixTransaction() throws Exception {
        onGet(URL + "/S1", "pagamento-pix.read", "{\"transacaoPix\":{\"valor\":10,\"status\":\"" + PixStatus.values()[0]
                + "\",\"endToEnd\":\"E2E\"},\"historico\":[{\"status\":\"" + PixStatus.values()[0] + "\"}]}");

        RetrievePixResponse response = client.retrievePixTransaction(config, "S1");

        assertNotNull(response.getPixTransaction());
        assertEquals(PixStatus.values()[0], response.getPixTransaction().getStatus());
        assertEquals("E2E", response.getPixTransaction().getEndToEnd());
        assertEquals(1, response.getHistory().size());
        verifyGet(URL + "/S1", "pagamento-pix.read", "Error retrieving pix");
    }

    @Test
    public void shouldWrapMalformedRetrieveResponse() {
        onGet(URL + "/S1", "pagamento-pix.read", NOT_JSON);
        assertWrapsParseFailure(() -> client.retrievePixTransaction(config, "S1"));
    }
}
