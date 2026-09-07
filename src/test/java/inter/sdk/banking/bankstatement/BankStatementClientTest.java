package inter.sdk.banking.bankstatement;

import inter.sdk.banking.enums.OperationType;
import inter.sdk.banking.enums.TransactionType;
import inter.sdk.banking.models.BankStatement;
import inter.sdk.banking.models.EnrichedBankStatementPage;
import inter.sdk.banking.models.EnrichedTransaction;
import inter.sdk.banking.models.FilterRetrieveEnrichedStatement;
import inter.sdk.commons.ClientTestSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BankStatementClientTest extends ClientTestSupport {

    private static final String SCOPE = "extrato.read";
    private static final String STATEMENT = BASE + "/banking/v2/extrato";
    private static final String ENRICHED = STATEMENT + "/completo";
    private static final String RANGE = "?dataInicio=2024-01-01&dataFim=2024-01-31";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final BankStatementClient client = new BankStatementClient();

    @Test
    public void shouldRetrieveStatement() throws Exception {
        onGet(STATEMENT + RANGE, SCOPE, "{\"transacoes\":[{\"cpmf\":\"0\",\"valor\":\"10.00\",\"titulo\":\"Pix\"}]}");

        BankStatement statement = client.retrieveStatement(config, "2024-01-01", "2024-01-31");

        assertEquals(1, statement.getTransactions().size());
        assertEquals("Pix", statement.getTransactions().get(0).getTitle());
        verifyGet(STATEMENT + RANGE, SCOPE, "Error retrieving statement");
    }

    @Test
    public void shouldWrapMalformedStatement() {
        onGet(STATEMENT + RANGE, SCOPE, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveStatement(config, "2024-01-01", "2024-01-31"));
    }

    @Test
    public void shouldWritePdfToFile() throws Exception {
        String pdf = Base64.getEncoder().encodeToString("%PDF-1.4 dummy".getBytes(StandardCharsets.UTF_8));
        onGet(STATEMENT + "/exportar" + RANGE, SCOPE, "{\"pdf\":\"" + pdf + "\"}");
        File out = new File(tmp.getRoot(), "statement.pdf");

        client.retrieveStatementInPdf(config, "2024-01-01", "2024-01-31", out.getAbsolutePath());

        assertEquals("%PDF-1.4 dummy", new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
        verifyGet(STATEMENT + "/exportar" + RANGE, SCOPE, "Error retrieving statement in pdf");
    }

    @Test
    public void shouldWrapPdfFailures() {
        onGet(STATEMENT + "/exportar" + RANGE, SCOPE, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveStatementInPdf(config, "2024-01-01", "2024-01-31", "ignored.pdf"));

        onGet(STATEMENT + "/exportar" + RANGE, SCOPE, "{\"pdf\":\"AA==\"}");
        String unwritable = new File(tmp.getRoot(), "missing-dir/x.pdf").getAbsolutePath();
        assertWrapsParseFailure(() -> client.retrieveStatementInPdf(config, "2024-01-01", "2024-01-31", unwritable));
    }

    @Test
    public void shouldRetrieveEnrichedPageWithPageSizeAndFilters() throws Exception {
        FilterRetrieveEnrichedStatement filter = FilterRetrieveEnrichedStatement.builder()
                .operationType(OperationType.C).transactionType(TransactionType.PIX).build();
        String url = ENRICHED + RANGE + "&pagina=2&tamanhoPagina=50&tipoOperacao=C&tipoTransacao=PIX";
        onGet(url, SCOPE, "{\"totalPaginas\":3,\"transacoes\":[{\"titulo\":\"t\"}]}");

        EnrichedBankStatementPage page = client.retrieveStatementPage(config, "2024-01-01", "2024-01-31", 2, 50, filter);

        assertEquals(Integer.valueOf(3), page.getTotalPages());
        assertEquals("t", page.getTransactions().get(0).getTitle());
        verifyGet(url, SCOPE, "Error retrieving enriched statement");
    }

    @Test
    public void shouldOmitEmptyFilterFields() throws Exception {
        String url = ENRICHED + RANGE + "&pagina=0&tipoOperacao=D";
        onGet(url, SCOPE, "{\"totalPaginas\":1,\"transacoes\":[]}");

        client.retrieveStatementPage(config, "2024-01-01", "2024-01-31", 0, null,
                FilterRetrieveEnrichedStatement.builder().operationType(OperationType.D).build());

        verifyGet(url, SCOPE, "Error retrieving enriched statement");
    }

    @Test
    public void shouldWalkAllEnrichedPages() throws Exception {
        onGet(ENRICHED + RANGE + "&pagina=0", SCOPE, "{\"totalPaginas\":2,\"transacoes\":[{\"titulo\":\"a\"}]}");
        onGet(ENRICHED + RANGE + "&pagina=1", SCOPE, "{\"totalPaginas\":2,\"transacoes\":[{\"titulo\":\"b\"},{\"titulo\":\"c\"}]}");

        List<EnrichedTransaction> all = client.retrieveStatementInRange(config, "2024-01-01", "2024-01-31", null);

        assertEquals(3, all.size());
        assertEquals("c", all.get(2).getTitle());
    }

    @Test
    public void shouldWrapMalformedEnrichedPage() {
        onGet(ENRICHED + RANGE + "&pagina=0", SCOPE, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveStatementInRange(config, "2024-01-01", "2024-01-31", null));
    }
}
