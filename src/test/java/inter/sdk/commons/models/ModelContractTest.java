package inter.sdk.commons.models;

import inter.sdk.commons.ModelContractTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Runs the reflective model contract (constructors, builder, accessors, equals/hashCode/toString)
 * against every class under the model packages (inter.sdk.*.models). Add new model classes to {@link #MODELS}.
 */
@RunWith(Parameterized.class)
public class ModelContractTest {

    static final List<Class<?>> MODELS = Arrays.<Class<?>>asList(
                inter.sdk.banking.models.Balance.class,
                inter.sdk.banking.models.BankDetails.class,
                inter.sdk.banking.models.BankStatement.class,
                inter.sdk.banking.models.Batch.class,
                inter.sdk.banking.models.BatchItem.class,
                inter.sdk.banking.models.BatchProcessing.class,
                inter.sdk.banking.models.BilletBatch.class,
                inter.sdk.banking.models.BilletPayment.class,
                inter.sdk.banking.models.CallbackError.class,
                inter.sdk.banking.models.CallbackPage.class,
                inter.sdk.banking.models.CallbackRetrieveFilter.class,
                inter.sdk.banking.models.CopyAndPaste.class,
                inter.sdk.banking.models.DarfPayment.class,
                inter.sdk.banking.models.DarfPaymentBatch.class,
                inter.sdk.banking.models.DarfPaymentResponse.class,
                inter.sdk.banking.models.DarfPaymentSearchFilter.class,
                inter.sdk.banking.models.EnrichedBankStatementPage.class,
                inter.sdk.banking.models.EnrichedTransaction.class,
                inter.sdk.banking.models.EnrichedTransactionDetails.class,
                inter.sdk.banking.models.FilterRetrieveEnrichedStatement.class,
                inter.sdk.banking.models.FinancialInstitution.class,
                inter.sdk.banking.models.IncludeBatchPaymentResponse.class,
                inter.sdk.banking.models.IncludeDarfPaymentResponse.class,
                inter.sdk.banking.models.IncludePaymentResponse.class,
                inter.sdk.banking.models.IncludePixResponse.class,
                inter.sdk.banking.models.Key.class,
                inter.sdk.banking.models.Payload.class,
                inter.sdk.banking.models.Payment.class,
                inter.sdk.banking.models.PaymentSearchFilter.class,
                inter.sdk.banking.models.Pix.class,
                inter.sdk.banking.models.PixHistoryEntity.class,
                inter.sdk.banking.models.PixTransaction.class,
                inter.sdk.banking.models.PixTransactionError.class,
                inter.sdk.banking.models.Receiver.class,
                inter.sdk.banking.models.Recipient.class,
                inter.sdk.banking.models.RetrieveCallbackResponse.class,
                inter.sdk.banking.models.RetrievePixResponse.class,
                inter.sdk.banking.models.Transaction.class,
                inter.sdk.banking.models.TransactionDetails.class,
                inter.sdk.billing.models.BaseBillingRetrievalFilter.class,
                inter.sdk.billing.models.BillingBilletRetrievingResponse.class,
                inter.sdk.billing.models.BillingCallbackPage.class,
                inter.sdk.billing.models.BillingIssueRequest.class,
                inter.sdk.billing.models.BillingIssueResponse.class,
                inter.sdk.billing.models.BillingPage.class,
                inter.sdk.billing.models.BillingPayload.class,
                inter.sdk.billing.models.BillingPixRetrievingResponse.class,
                inter.sdk.billing.models.BillingRetrievalFilter.class,
                inter.sdk.billing.models.BillingRetrieveCallbackResponse.class,
                inter.sdk.billing.models.BillingRetrieveCallbacksFilter.class,
                inter.sdk.billing.models.BillingRetrievingResponse.class,
                inter.sdk.billing.models.CancelBillingRequest.class,
                inter.sdk.billing.models.Discount.class,
                inter.sdk.billing.models.Fine.class,
                inter.sdk.billing.models.Message.class,
                inter.sdk.billing.models.Mora.class,
                inter.sdk.billing.models.Person.class,
                inter.sdk.billing.models.RetrievedBilling.class,
                inter.sdk.billing.models.Sorting.class,
                inter.sdk.billing.models.Summary.class,
                inter.sdk.billing.models.SummaryItem.class,
                inter.sdk.commons.models.AbstractModel.class,
                inter.sdk.commons.models.Config.class,
                inter.sdk.commons.models.Error.class,
                inter.sdk.commons.models.GetTokenResponse.class,
                inter.sdk.commons.models.IncludeWebhookRequest.class,
                inter.sdk.commons.models.PdfReturn.class,
                inter.sdk.commons.models.Violation.class,
                inter.sdk.commons.models.Webhook.class,
                inter.sdk.pix.models.AdditionalInfo.class,
                inter.sdk.pix.models.BillingPage.class,
                inter.sdk.pix.models.Calendar.class,
                inter.sdk.pix.models.CallbackRetrieveFilter.class,
                inter.sdk.pix.models.Change.class,
                inter.sdk.pix.models.CobMoment.class,
                inter.sdk.pix.models.ComponentValue.class,
                inter.sdk.pix.models.Debtor.class,
                inter.sdk.pix.models.DetailedDevolution.class,
                inter.sdk.pix.models.DetailedDuePixBilling.class,
                inter.sdk.pix.models.DetailedImmediatePixBilling.class,
                inter.sdk.pix.models.DevolutionRequestBody.class,
                inter.sdk.pix.models.Discount.class,
                inter.sdk.pix.models.DueBilling.class,
                inter.sdk.pix.models.DueBillingBatch.class,
                inter.sdk.pix.models.DueBillingBatchPage.class,
                inter.sdk.pix.models.DueBillingBatchSummary.class,
                inter.sdk.pix.models.DueBillingCalendar.class,
                inter.sdk.pix.models.DueBillingEntity.class,
                inter.sdk.pix.models.DueBillingPage.class,
                inter.sdk.pix.models.DueBillingValue.class,
                inter.sdk.pix.models.Fees.class,
                inter.sdk.pix.models.Fine.class,
                inter.sdk.pix.models.FixedDateDiscount.class,
                inter.sdk.pix.models.GeneratedDueBilling.class,
                inter.sdk.pix.models.GeneratedImmediateBilling.class,
                inter.sdk.pix.models.IncludeDueBillingBatchRequest.class,
                inter.sdk.pix.models.IncludeLocationRequest.class,
                inter.sdk.pix.models.ItemPayload.class,
                inter.sdk.pix.models.Location.class,
                inter.sdk.pix.models.LocationPage.class,
                inter.sdk.pix.models.Pagination.class,
                inter.sdk.pix.models.Parameters.class,
                inter.sdk.pix.models.Pix.class,
                inter.sdk.pix.models.PixBilling.class,
                inter.sdk.pix.models.PixCallbackPage.class,
                inter.sdk.pix.models.PixCharge.class,
                inter.sdk.pix.models.PixPage.class,
                inter.sdk.pix.models.PixPayload.class,
                inter.sdk.pix.models.PixValue.class,
                inter.sdk.pix.models.Problem.class,
                inter.sdk.pix.models.Receiver.class,
                inter.sdk.pix.models.Reduction.class,
                inter.sdk.pix.models.RetrieveCallbackResponse.class,
                inter.sdk.pix.models.RetrieveDueBillingFilter.class,
                inter.sdk.pix.models.RetrieveImmediateBillingsFilter.class,
                inter.sdk.pix.models.RetrieveLocationFilter.class,
                inter.sdk.pix.models.RetrievedPixFilter.class,
                inter.sdk.pix.models.ValueComponent.class,
                inter.sdk.pix.models.Violation.class,
                inter.sdk.pix.models.Withdrawal.class,
                inter.sdk.pix.models.WithdrawalTransaction.class
    );

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> params = new ArrayList<>();
        for (Class<?> c : MODELS) {
            params.add(new Object[]{c});
        }
        return params;
    }

    private final Class<?> model;

    public ModelContractTest(Class<?> model) {
        this.model = model;
    }

    @Test
    public void shouldHonourModelContract() throws Exception {
        ModelContractTester.verify(model);
    }
}
