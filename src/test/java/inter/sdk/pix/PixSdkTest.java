package inter.sdk.pix;

import inter.sdk.commons.TestFixtures;
import inter.sdk.commons.exceptions.SdkException;
import inter.sdk.commons.models.Config;
import inter.sdk.pix.duebilling.DueBillingClient;
import inter.sdk.pix.duebillingbatch.DueBillingBatchClient;
import inter.sdk.pix.enums.ImmediateBillingType;
import inter.sdk.pix.immediatebillings.ImmediateBillingClient;
import inter.sdk.pix.locations.LocationClient;
import inter.sdk.pix.models.CallbackRetrieveFilter;
import inter.sdk.pix.models.DevolutionRequestBody;
import inter.sdk.pix.models.DueBilling;
import inter.sdk.pix.models.IncludeDueBillingBatchRequest;
import inter.sdk.pix.models.PixBilling;
import inter.sdk.pix.models.RetrieveDueBillingFilter;
import inter.sdk.pix.models.RetrieveImmediateBillingsFilter;
import inter.sdk.pix.models.RetrieveLocationFilter;
import inter.sdk.pix.models.RetrievedPixFilter;
import inter.sdk.pix.pix.PixClient;
import inter.sdk.pix.webhooks.PixWebhookSdk;
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
public class PixSdkTest {

    private static final String FROM = "2024-01-01";
    private static final String TO = "2024-01-31";

    private Config config;
    private MockedConstruction<DueBillingClient> dueBilling;
    private MockedConstruction<DueBillingBatchClient> dueBillingBatch;
    private MockedConstruction<ImmediateBillingClient> immediate;
    private MockedConstruction<LocationClient> locations;
    private MockedConstruction<PixClient> pix;
    private MockedConstruction<PixWebhookSdk> webhooks;

    private interface Call {
        void on(PixSdk sdk) throws SdkException;
    }

    @Before
    public void setUp() {
        config = TestFixtures.config();
        dueBilling = mockConstruction(DueBillingClient.class);
        dueBillingBatch = mockConstruction(DueBillingBatchClient.class);
        immediate = mockConstruction(ImmediateBillingClient.class);
        locations = mockConstruction(LocationClient.class);
        pix = mockConstruction(PixClient.class);
        webhooks = mockConstruction(PixWebhookSdk.class);
    }

    @After
    public void tearDown() {
        webhooks.close();
        pix.close();
        locations.close();
        immediate.close();
        dueBillingBatch.close();
        dueBilling.close();
    }

    private void twice(Call call) throws SdkException {
        PixSdk sdk = new PixSdk(config);
        call.on(sdk);
        call.on(sdk);
    }

    @Test
    public void shouldDelegateDueBillingCalls() throws Exception {
        DueBilling billing = DueBilling.builder().build();
        RetrieveDueBillingFilter filter = RetrieveDueBillingFilter.builder().build();
        twice(s -> s.includeDuePixBilling("tx", billing));
        twice(s -> s.retrieveDuePixBilling("tx"));
        twice(s -> s.retrieveBillingCollection(FROM, TO, filter));
        twice(s -> s.retrieveBillingCollection(FROM, TO, 1, 10, filter));
        twice(s -> s.reviewDuePixBilling("tx", billing));

        assertEquals(5, dueBilling.constructed().size());
        verify(dueBilling.constructed().get(0), times(2)).includeDueBilling(config, "tx", billing);
        verify(dueBilling.constructed().get(1), times(2)).retrieveDueBilling(config, "tx");
        verify(dueBilling.constructed().get(2), times(2)).retrieveDuePixBillingInRange(config, FROM, TO, filter);
        verify(dueBilling.constructed().get(3), times(2)).retrieveDueBillingPage(config, FROM, TO, 1, 10, filter);
        verify(dueBilling.constructed().get(4), times(2)).reviewDueBilling(config, "tx", billing);
    }

    @Test
    public void shouldDelegateDueBillingBatchCalls() throws Exception {
        IncludeDueBillingBatchRequest request = IncludeDueBillingBatchRequest.builder().build();
        twice(s -> s.includeDueBillingBatch("B1", request));
        twice(s -> s.retrieveDueBillingBatch("B1"));
        twice(s -> s.retrieveDueBillingBatchCollection(FROM, TO, 1, 10));
        twice(s -> s.retrieveDueBillingBatchCollection(FROM, TO));
        twice(s -> s.retrieveDueBillingBatchBySituation("B1", "CRIADA"));
        twice(s -> s.retrieveDueBillingBatchSummary("B1"));
        twice(s -> s.reviewDueBillingBatch("B1", request));

        assertEquals(7, dueBillingBatch.constructed().size());
        verify(dueBillingBatch.constructed().get(0), times(2)).includeDueBillingBatch(config, "B1", request);
        verify(dueBillingBatch.constructed().get(1), times(2)).retrieveDueBillingBatch(config, "B1");
        verify(dueBillingBatch.constructed().get(2), times(2)).retrieveDueBillingBatchPage(config, FROM, TO, 1, 10);
        verify(dueBillingBatch.constructed().get(3), times(2)).retrieveDueBillingBatchInRange(config, FROM, TO);
        verify(dueBillingBatch.constructed().get(4), times(2)).retrieveDueBillingBatchBySituation(config, "B1", "CRIADA");
        verify(dueBillingBatch.constructed().get(5), times(2)).retrieveDueBillingBatchSummary(config, "B1");
        verify(dueBillingBatch.constructed().get(6), times(2)).reviewDueBillingBatch(config, "B1", request);
    }

    @Test
    public void shouldDelegateImmediateBillingCalls() throws Exception {
        PixBilling billing = PixBilling.builder().build();
        RetrieveImmediateBillingsFilter filter = RetrieveImmediateBillingsFilter.builder().build();
        twice(s -> s.includeImmediateBilling(billing));
        twice(s -> s.retrieveImmediateBilling("tx"));
        twice(s -> s.retrieveImmediateBillingList(FROM, TO, filter));
        twice(s -> s.retrieveImmediateBillingList(FROM, TO, 1, 10, filter));
        twice(s -> s.reviewImmediateBilling(billing));

        assertEquals(5, immediate.constructed().size());
        verify(immediate.constructed().get(0), times(2)).includeImmediateBilling(config, billing);
        verify(immediate.constructed().get(1), times(2)).retrieveImmediateBilling(config, "tx");
        verify(immediate.constructed().get(2), times(2)).retrieveImmediateBillingInRange(config, FROM, TO, filter);
        verify(immediate.constructed().get(3), times(2)).retrieveImmediateBillingPage(config, FROM, TO, 1, 10, filter);
        verify(immediate.constructed().get(4), times(2)).reviewImmediateBilling(config, billing);
    }

    @Test
    public void shouldDelegateLocationCalls() throws Exception {
        RetrieveLocationFilter filter = RetrieveLocationFilter.builder().build();
        twice(s -> s.includeLocation(ImmediateBillingType.cob));
        twice(s -> s.retrieveLocation("7"));
        twice(s -> s.retrieveLocationsList(FROM, TO, filter));
        twice(s -> s.retrieveLocationsList(FROM, TO, 1, 10, filter));
        twice(s -> s.unlinkLocation("7"));

        assertEquals(5, locations.constructed().size());
        verify(locations.constructed().get(0), times(2)).includeLocation(config, ImmediateBillingType.cob);
        verify(locations.constructed().get(1), times(2)).retrieveLocation(config, "7");
        verify(locations.constructed().get(2), times(2)).retrieveLocationInRange(config, FROM, TO, filter);
        verify(locations.constructed().get(3), times(2)).retrieveLocationPage(config, FROM, TO, 1, 10, filter);
        verify(locations.constructed().get(4), times(2)).unlinkLocation(config, "7");
    }

    @Test
    public void shouldDelegatePixCalls() throws Exception {
        DevolutionRequestBody body = DevolutionRequestBody.builder().build();
        RetrievedPixFilter filter = RetrievedPixFilter.builder().build();
        twice(s -> s.requestDevolution("E1", "D1", body));
        twice(s -> s.retrieveDevolution("E1", "D1"));
        twice(s -> s.retrievePixList(FROM, TO, filter));
        twice(s -> s.retrievePixList(FROM, TO, 1, 10, filter));
        twice(s -> s.retrievePix("E1"));

        assertEquals(5, pix.constructed().size());
        verify(pix.constructed().get(0), times(2)).requestDevolution(config, "E1", "D1", body);
        verify(pix.constructed().get(1), times(2)).retrieveDevolution(config, "E1", "D1");
        verify(pix.constructed().get(2), times(2)).retrievePixInRange(config, FROM, TO, filter);
        verify(pix.constructed().get(3), times(2)).retrievePixPage(config, FROM, TO, 1, 10, filter);
        verify(pix.constructed().get(4), times(2)).retrievePixTransaction(config, "E1");
    }

    @Test
    public void shouldDelegateWebhookCalls() throws Exception {
        CallbackRetrieveFilter filter = CallbackRetrieveFilter.builder().build();
        twice(s -> s.retrieveCallbacks(FROM, TO, filter));
        twice(s -> s.retrieveCallbacks(FROM, TO, 1, 10, filter));
        twice(s -> s.includeWebhook("key", "https://hook"));
        twice(s -> s.retrieveWebhook("key"));
        twice(s -> s.deleteWebhook("key"));

        assertEquals(5, webhooks.constructed().size());
        verify(webhooks.constructed().get(0), times(2)).retrieveCallbackInRange(config, FROM, TO, filter);
        verify(webhooks.constructed().get(1), times(2)).retrieveCallbackPage(config, FROM, TO, 1, 10, filter);
        verify(webhooks.constructed().get(2), times(2)).includeWebhook(config, "key", "https://hook");
        verify(webhooks.constructed().get(3), times(2)).retrieveWebhook(config, "key");
        verify(webhooks.constructed().get(4), times(2)).deleteWebhook(config, "key");
    }
}
