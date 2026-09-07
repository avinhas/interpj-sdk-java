package inter.sdk.billing;

import inter.sdk.billing.billing.BillingClient;
import inter.sdk.billing.models.BillingIssueRequest;
import inter.sdk.billing.models.BillingRetrievalFilter;
import inter.sdk.billing.models.BillingRetrieveCallbacksFilter;
import inter.sdk.billing.models.Sorting;
import inter.sdk.billing.webhooks.BillingWebhookClient;
import inter.sdk.commons.TestFixtures;
import inter.sdk.commons.exceptions.SdkException;
import inter.sdk.commons.models.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Every facade method lazily instantiates its client. Each method is invoked twice on a fresh
 * facade so both the "create" and "reuse" branches run, and exactly one client is built per method.
 */
public class BillingSdkTest {

    private static final String FROM = "2024-01-01";
    private static final String TO = "2024-01-31";

    private Config config;
    private MockedConstruction<BillingClient> billing;
    private MockedConstruction<BillingWebhookClient> webhooks;

    private interface Call {
        void on(BillingSdk sdk) throws SdkException;
    }

    @Before
    public void setUp() {
        config = TestFixtures.config();
        billing = mockConstruction(BillingClient.class);
        webhooks = mockConstruction(BillingWebhookClient.class);
    }

    @After
    public void tearDown() {
        webhooks.close();
        billing.close();
    }

    private void twice(Call call) throws SdkException {
        BillingSdk sdk = new BillingSdk(config);
        call.on(sdk);
        call.on(sdk);
    }

    @Test
    public void shouldDelegateBillingCalls() throws Exception {
        BillingIssueRequest request = BillingIssueRequest.builder().build();
        BillingRetrievalFilter filter = BillingRetrievalFilter.builder().build();
        Sorting sort = Sorting.builder().build();
        twice(s -> s.cancelBilling("R1", "reason"));
        twice(s -> s.issueBilling(request));
        twice(s -> s.retrieveBilling("R1"));
        twice(s -> s.retrieveBillingCollection(FROM, TO, filter, sort));
        twice(s -> s.retrieveBillingCollection(FROM, TO, 1, 10, filter, sort));
        twice(s -> s.retrieveBillingPdf("R1", "out.pdf"));
        twice(s -> s.retrieveBillingSummary(FROM, TO, filter));

        assertEquals(7, billing.constructed().size());
        verify(billing.constructed().get(0), times(2)).cancelBilling(config, "R1", "reason");
        verify(billing.constructed().get(1), times(2)).issueBilling(config, request);
        verify(billing.constructed().get(2), times(2)).retrieveBilling(config, "R1");
        verify(billing.constructed().get(3), times(2)).retrieveBillingsInRange(config, FROM, TO, filter, sort);
        verify(billing.constructed().get(4), times(2)).retrieveBillingPage(config, FROM, TO, 1, 10, filter, sort);
        verify(billing.constructed().get(5), times(2)).retrieveBillingInPDF(config, "R1", "out.pdf");
        verify(billing.constructed().get(6), times(2)).retrieveBillingSummary(config, FROM, TO, filter);
    }

    @Test
    public void shouldDelegateWebhookCalls() throws Exception {
        BillingRetrieveCallbacksFilter filter = BillingRetrieveCallbacksFilter.builder().build();
        twice(s -> s.retrieveCallbacks(FROM, TO, filter));
        twice(s -> s.retrieveCallbacks(FROM, TO, 1, 10, filter));
        twice(s -> s.includeWebhook("https://hook"));
        twice(s -> s.retrieveWebhook());
        twice(s -> s.deleteWebhook());

        assertEquals(5, webhooks.constructed().size());
        verify(webhooks.constructed().get(0), times(2)).retrieveCallbacksInRange(config, FROM, TO, filter);
        verify(webhooks.constructed().get(1), times(2)).retrieveCallbackPage(config, FROM, TO, 1, 10, filter);
        verify(webhooks.constructed().get(2), times(2)).includeWebhook(config, "https://hook");
        verify(webhooks.constructed().get(3), times(2)).retrieveWebhook(config);
        verify(webhooks.constructed().get(4), times(2)).deleteWebhook(config);
    }
}
