package inter.sdk.banking;

import inter.sdk.banking.balance.BalanceClient;
import inter.sdk.banking.bankstatement.BankStatementClient;
import inter.sdk.banking.models.BilletPayment;
import inter.sdk.banking.models.CallbackRetrieveFilter;
import inter.sdk.banking.models.DarfPayment;
import inter.sdk.banking.models.DarfPaymentSearchFilter;
import inter.sdk.banking.models.FilterRetrieveEnrichedStatement;
import inter.sdk.banking.models.PaymentSearchFilter;
import inter.sdk.banking.models.Pix;
import inter.sdk.banking.payments.BankingPaymentClient;
import inter.sdk.banking.pix.BankingPixClient;
import inter.sdk.banking.webhooks.BankingWebhookClient;
import inter.sdk.commons.TestFixtures;
import inter.sdk.commons.exceptions.SdkException;
import inter.sdk.commons.models.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Every facade method lazily instantiates its client. Each method is invoked twice on a fresh
 * facade so both the "create" and "reuse" branches run, and exactly one client is built per method.
 */
public class BankingSdkTest {

    private static final String FROM = "2024-01-01";
    private static final String TO = "2024-01-31";

    private Config config;
    private MockedConstruction<BankStatementClient> statements;
    private MockedConstruction<BalanceClient> balances;
    private MockedConstruction<BankingPaymentClient> payments;
    private MockedConstruction<BankingPixClient> pix;
    private MockedConstruction<BankingWebhookClient> webhooks;

    private interface Call {
        void on(BankingSdk sdk) throws SdkException;
    }

    @Before
    public void setUp() {
        config = TestFixtures.config();
        statements = mockConstruction(BankStatementClient.class);
        balances = mockConstruction(BalanceClient.class);
        payments = mockConstruction(BankingPaymentClient.class);
        pix = mockConstruction(BankingPixClient.class);
        webhooks = mockConstruction(BankingWebhookClient.class);
    }

    @After
    public void tearDown() {
        webhooks.close();
        pix.close();
        payments.close();
        balances.close();
        statements.close();
    }

    private void twice(Call call) throws SdkException {
        BankingSdk sdk = new BankingSdk(config);
        call.on(sdk);
        call.on(sdk);
    }

    @Test
    public void shouldDelegateStatementCalls() throws Exception {
        FilterRetrieveEnrichedStatement filter = FilterRetrieveEnrichedStatement.builder().build();
        twice(s -> s.retrieveStatement(FROM, TO));
        twice(s -> s.retrieveStatementInPdf(FROM, TO, "out.pdf"));
        twice(s -> s.retrieveEnrichedStatement(FROM, TO, filter));
        twice(s -> s.retrieveEnrichedStatement(FROM, TO, filter, 1));
        twice(s -> s.retrieveEnrichedStatement(FROM, TO, filter, 1, 50));

        assertEquals(5, statements.constructed().size());
        verify(statements.constructed().get(0), times(2)).retrieveStatement(config, FROM, TO);
        verify(statements.constructed().get(1), times(2)).retrieveStatementInPdf(config, FROM, TO, "out.pdf");
        verify(statements.constructed().get(2), times(2)).retrieveStatementInRange(config, FROM, TO, filter);
        verify(statements.constructed().get(3), times(2)).retrieveStatementPage(config, FROM, TO, 1, null, filter);
        verify(statements.constructed().get(4), times(2)).retrieveStatementPage(config, FROM, TO, 1, 50, filter);
    }

    @Test
    public void shouldDelegateBalanceCalls() throws Exception {
        twice(s -> s.retrieveBalance(FROM));

        assertEquals(1, balances.constructed().size());
        verify(balances.constructed().get(0), times(2)).retrieve_balance(config, FROM);
    }

    @Test
    public void shouldDelegatePaymentCalls() throws Exception {
        BilletPayment billet = BilletPayment.builder().build();
        DarfPayment darf = DarfPayment.builder().build();
        PaymentSearchFilter paymentFilter = PaymentSearchFilter.builder().build();
        DarfPaymentSearchFilter darfFilter = DarfPaymentSearchFilter.builder().build();
        twice(s -> s.includePayment(billet));
        twice(s -> s.retrievePayment(FROM, TO, paymentFilter));
        twice(s -> s.includeDarfPayment(darf));
        twice(s -> s.retrieveDarfPayments(FROM, TO, darfFilter));
        twice(s -> s.includeBatchPayment("my-id", Collections.emptyList()));
        twice(s -> s.retrievePaymentBatch("B1"));
        twice(s -> s.paymentSchedulingCancel("T1"));

        assertEquals(7, payments.constructed().size());
        verify(payments.constructed().get(0), times(2)).includeBilletPayment(config, billet);
        verify(payments.constructed().get(1), times(2)).retrievePaymentList(config, FROM, TO, paymentFilter);
        verify(payments.constructed().get(2), times(2)).includeDarfPayment(config, darf);
        verify(payments.constructed().get(3), times(2)).retrieveDarfPayment(config, FROM, TO, darfFilter);
        verify(payments.constructed().get(4), times(2)).includePaymentInBatch(config, "my-id", Collections.emptyList());
        verify(payments.constructed().get(5), times(2)).retrieveBatch(config, "B1");
        verify(payments.constructed().get(6), times(2)).cancelPayment(config, "T1");
    }

    @Test
    public void shouldDelegatePixCalls() throws Exception {
        Pix request = Pix.builder().build();
        twice(s -> s.includePix(request));
        twice(s -> s.retrievePix("R1"));

        assertEquals(2, pix.constructed().size());
        verify(pix.constructed().get(0), times(2)).includePix(config, request);
        verify(pix.constructed().get(1), times(2)).retrievePixTransaction(config, "R1");
    }

    @Test
    public void shouldDelegateWebhookCalls() throws Exception {
        CallbackRetrieveFilter filter = CallbackRetrieveFilter.builder().build();
        twice(s -> s.includeWebhook("pix-pagamento", "https://hook"));
        twice(s -> s.retrieveWebhook("pix-pagamento"));
        twice(s -> s.deleteWebhook("pix-pagamento"));
        twice(s -> s.retrieveCallback("pix-pagamento", FROM, TO, filter));
        twice(s -> s.retrieveCallback("pix-pagamento", FROM, TO, filter, 2, 10));

        assertEquals(5, webhooks.constructed().size());
        verify(webhooks.constructed().get(0), times(2)).includeWebhook(config, "pix-pagamento", "https://hook");
        verify(webhooks.constructed().get(1), times(2)).retrieveWebhook(config, "pix-pagamento");
        verify(webhooks.constructed().get(2), times(2)).deleteWebhook(config, "pix-pagamento");
        verify(webhooks.constructed().get(3), times(2)).retrieveCallbacksInRange(config, "pix-pagamento", FROM, TO, filter);
        verify(webhooks.constructed().get(4), times(2)).retrieveCallbackPage(config, "pix-pagamento", FROM, TO, 2, null, filter);
    }
}
