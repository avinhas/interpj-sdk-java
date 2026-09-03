package inter.sdk.banking.payments;

import inter.sdk.banking.enums.DarfPaymentDateType;
import inter.sdk.banking.enums.PaymentDateType;
import inter.sdk.banking.models.BatchItem;
import inter.sdk.banking.models.BatchProcessing;
import inter.sdk.banking.models.BilletBatch;
import inter.sdk.banking.models.BilletPayment;
import inter.sdk.banking.models.DarfPayment;
import inter.sdk.banking.models.DarfPaymentBatch;
import inter.sdk.banking.models.DarfPaymentResponse;
import inter.sdk.banking.models.DarfPaymentSearchFilter;
import inter.sdk.banking.models.IncludeBatchPaymentResponse;
import inter.sdk.banking.models.IncludeDarfPaymentResponse;
import inter.sdk.banking.models.IncludePaymentResponse;
import inter.sdk.banking.models.Payment;
import inter.sdk.banking.models.PaymentSearchFilter;
import inter.sdk.commons.ClientTestSupport;
import inter.sdk.commons.exceptions.ServerException;
import inter.sdk.commons.models.Error;
import inter.sdk.commons.utils.HttpUtils;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

public class BankingPaymentClientTest extends ClientTestSupport {

    private static final String PAYMENT = BASE + "/banking/v2/pagamento";
    private static final String DARF = PAYMENT + "/darf";
    private static final String BATCH = PAYMENT + "/lote";
    private static final String RANGE = "?dataInicio=2024-01-01&dataFim=2024-01-31";

    private final BankingPaymentClient client = new BankingPaymentClient();

    @Test
    public void shouldCancelPayment() throws Exception {
        client.cancelPayment(config, "tx-1");
        verifyDelete(PAYMENT + "/tx-1", "pagamento-boleto.write", "Error canceling payment scheduling");
    }

    @Test
    public void shouldIncludePaymentsInBatch() throws Exception {
        onPost(BATCH, "pagamento-lote.write", "{\"idLote\":\"L1\",\"status\":\"EM_PROCESSAMENTO\",\"meuIdentificador\":\"my-id\",\"qtdePagamentos\":2}");
        List<BatchItem> items = Arrays.asList(
                BilletBatch.builder().barcode("123").amountToPay(new BigDecimal("10.00")).build(),
                DarfPaymentBatch.builder().revenueCode("0190").value("5").build());

        IncludeBatchPaymentResponse response = client.includePaymentInBatch(config, "my-id", items);

        assertEquals("L1", response.getBatchId());
        assertEquals(Integer.valueOf(2), response.getPaymentQuantity());
        verifyPost(BATCH, "pagamento-lote.write", "Error including payment in batch",
                "\"meuIdentificador\" : \"my-id\"", "\"tipoPagamento\" : \"BOLETO\"", "\"tipoPagamento\" : \"DARF\"", "\"codBarraLinhaDigitavel\" : \"123\"");
    }

    @Test
    public void shouldWrapMalformedBatchResponse() {
        onPost(BATCH, "pagamento-lote.write", NOT_JSON);
        assertWrapsParseFailure(() -> client.includePaymentInBatch(config, "id", Collections.emptyList()));
    }

    @Test
    public void shouldIncludeDarfPayment() throws Exception {
        onPost(DARF, "pagamento-darf.write", "{\"aprovacoesNecessarias\":1,\"aprovacoesRealizadas\":0,\"codigoSolicitacao\":\"S1\"}");
        DarfPayment darf = DarfPayment.builder().revenueCode("0190").cnpjOrCpf("123").value("5.00").build();

        IncludeDarfPaymentResponse response = client.includeDarfPayment(config, darf);

        assertEquals("S1", response.getRequestCode());
        verifyPost(DARF, "pagamento-darf.write", "Error including DARF payment", "\"codigoReceita\" : \"0190\"", "\"cnpjCpf\" : \"123\"");
    }

    @Test
    public void shouldWrapMalformedDarfResponse() {
        onPost(DARF, "pagamento-darf.write", NOT_JSON);
        assertWrapsParseFailure(() -> client.includeDarfPayment(config, DarfPayment.builder().build()));
    }

    @Test
    public void shouldIncludeBilletPayment() throws Exception {
        onPost(PAYMENT, "pagamento-boleto.write", "{\"quantidadeAprovadores\":\"1\",\"statusPagamento\":\"APROVADO\",\"codigoTransacao\":\"T1\"}");
        BilletPayment payment = BilletPayment.builder().barcode("bar").amountToPay(BigDecimal.TEN).build();

        IncludePaymentResponse response = client.includeBilletPayment(config, payment);

        assertEquals("T1", response.getTransactionCode());
        assertEquals("APROVADO", response.getPaymentStatus());
        verifyPost(PAYMENT, "pagamento-boleto.write", "Error including payment", "\"codBarraLinhaDigitavel\" : \"bar\"", "\"valorPagar\" : 10");
    }

    @Test
    public void shouldWrapMalformedBilletResponse() {
        onPost(PAYMENT, "pagamento-boleto.write", NOT_JSON);
        assertWrapsParseFailure(() -> client.includeBilletPayment(config, BilletPayment.builder().build()));
    }

    @Test
    public void shouldRetrieveDarfPaymentsWithAllFilters() throws Exception {
        String url = DARF + RANGE + "&codigoTransacao=T1&codigoReceita=0190&filtrarDataPor=INCLUSAO";
        onGet(url, "pagamento-boleto.read", "[{\"codigoSolicitacao\":\"S1\",\"valor\":1.5},{\"codigoSolicitacao\":\"S2\"}]");
        DarfPaymentSearchFilter filter = DarfPaymentSearchFilter.builder()
                .requestCode("T1").revenueCode("0190").filterDateBy(DarfPaymentDateType.INCLUSAO).build();

        List<DarfPaymentResponse> result = client.retrieveDarfPayment(config, "2024-01-01", "2024-01-31", filter);

        assertEquals(2, result.size());
        assertEquals(new BigDecimal("1.5"), result.get(0).getAmount());
        verifyGet(url, "pagamento-boleto.read", "Error retrieving DARF payment");
    }

    @Test
    public void shouldRetrieveDarfPaymentsWithoutFilter() throws Exception {
        onGet(DARF + RANGE, "pagamento-boleto.read", "[]");
        assertTrue(client.retrieveDarfPayment(config, "2024-01-01", "2024-01-31", null).isEmpty());

        onGet(DARF + RANGE + "&codigoReceita=1", "pagamento-boleto.read", "[]");
        assertTrue(client.retrieveDarfPayment(config, "2024-01-01", "2024-01-31",
                DarfPaymentSearchFilter.builder().revenueCode("1").build()).isEmpty());
    }

    @Test
    public void shouldWrapMalformedDarfList() {
        onGet(DARF + RANGE, "pagamento-boleto.read", NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveDarfPayment(config, "2024-01-01", "2024-01-31", null));
    }

    @Test
    public void shouldRetrieveBatchSplittingBilletAndDarfItems() throws Exception {
        onGet(BATCH + "/L1", "pagamento-lote.read", "{\"idLote\":\"L1\",\"status\":\"OK\",\"qtdePagamentos\":2,\"pagamentos\":["
                + "{\"tipoPagamento\":\"BILLET\",\"codBarraLinhaDigitavel\":\"123\",\"status\":\"PAGO\"},"
                + "{\"tipoPagamento\":\"DARF\",\"codigoReceita\":\"0190\",\"status\":\"PENDENTE\"}]}");

        BatchProcessing batch = client.retrieveBatch(config, "L1");

        assertEquals("L1", batch.getBatchId());
        assertEquals(2, batch.getPayments().size());
        assertTrue(batch.getPayments().get(0) instanceof BilletBatch);
        assertEquals("123", ((BilletBatch) batch.getPayments().get(0)).getBarcode());
        assertTrue(batch.getPayments().get(1) instanceof DarfPaymentBatch);
        assertEquals("0190", ((DarfPaymentBatch) batch.getPayments().get(1)).getRevenueCode());
        verifyGet(BATCH + "/L1", "pagamento-lote.read", "Error to retrieve batch");
    }

    @Test
    public void shouldRetrieveBatchWithoutPayments() throws Exception {
        onGet(BATCH + "/L2", "pagamento-lote.read", "{\"idLote\":\"L2\",\"status\":\"OK\"}");

        BatchProcessing batch = client.retrieveBatch(config, "L2");

        assertEquals("L2", batch.getBatchId());
        assertTrue(batch.getPayments().isEmpty());
    }

    @Test
    public void shouldWrapUnparseableBatch() {
        onGet(BATCH + "/L3", "pagamento-lote.read", "{not json");
        assertWrapsParseFailure(() -> client.retrieveBatch(config, "L3"));

        onGet(BATCH + "/L4", "pagamento-lote.read", "{\"pagamentos\":[{\"tipoPagamento\":\"BILLET\",\"valorPagar\":\"not-a-number\"}]}");
        assertWrapsParseFailure(() -> client.retrieveBatch(config, "L4"));
    }

    @Test
    public void shouldRetrievePaymentListWithFilters() throws Exception {
        String url = PAYMENT + RANGE + "&codBarraLinhaDigitavel=bar&codigoTransacao=T1&filtrarDataPor=PAGAMENTO";
        onGet(url, "pagamento-boleto.read", "[{\"codigoTransacao\":\"T1\",\"valorPago\":10.00}]");
        PaymentSearchFilter filter = PaymentSearchFilter.builder()
                .barcode("bar").transactionCode("T1").filterDateBy(PaymentDateType.PAGAMENTO).build();

        List<Payment> payments = client.retrievePaymentList(config, "2024-01-01", "2024-01-31", filter);

        assertEquals(1, payments.size());
        assertEquals("T1", payments.get(0).getTransactionCode());
        verifyGet(url, "pagamento-boleto.read", "Error retrieving payments");
    }

    @Test
    public void shouldRetrievePaymentListWithPartialOrNoFilter() throws Exception {
        onGet(PAYMENT + RANGE, "pagamento-boleto.read", "[]");
        assertTrue(client.retrievePaymentList(config, "2024-01-01", "2024-01-31", null).isEmpty());

        onGet(PAYMENT + RANGE + "&codigoTransacao=T2", "pagamento-boleto.read", "[]");
        assertTrue(client.retrievePaymentList(config, "2024-01-01", "2024-01-31",
                PaymentSearchFilter.builder().transactionCode("T2").build()).isEmpty());
    }

    @Test
    public void shouldWrapMalformedPaymentList() {
        onGet(PAYMENT + RANGE, "pagamento-boleto.read", NOT_JSON);
        assertWrapsParseFailure(() -> client.retrievePaymentList(config, "2024-01-01", "2024-01-31", null));
    }

    @Test
    public void shouldPropagateServerErrors() {
        ServerException boom = new ServerException("Error retrieving payments", Error.builder().title("500").build());
        http.when(() -> HttpUtils.callGet(eq(config), eq(PAYMENT + RANGE), eq("pagamento-boleto.read"), anyString())).thenThrow(boom);
        assertPropagates(boom, () -> client.retrievePaymentList(config, "2024-01-01", "2024-01-31", null));
    }
}
