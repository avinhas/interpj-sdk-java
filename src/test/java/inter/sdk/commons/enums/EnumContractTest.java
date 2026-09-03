package inter.sdk.commons.enums;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Exercises values()/valueOf()/toString() of every SDK enum. */
@RunWith(Parameterized.class)
public class EnumContractTest {

    static final List<Class<? extends Enum<?>>> ENUMS = Arrays.<Class<? extends Enum<?>>>asList(
                inter.sdk.banking.enums.AccountType.class,
                inter.sdk.banking.enums.DarfPaymentDateType.class,
                inter.sdk.banking.enums.DiscountCode.class,
                inter.sdk.banking.enums.OperationType.class,
                inter.sdk.banking.enums.PaymentDateType.class,
                inter.sdk.banking.enums.PixStatus.class,
                inter.sdk.banking.enums.TransactionType.class,
                inter.sdk.billing.enums.BillingDateType.class,
                inter.sdk.billing.enums.BillingSituation.class,
                inter.sdk.billing.enums.BillingType.class,
                inter.sdk.billing.enums.DiscountCode.class,
                inter.sdk.billing.enums.FineCode.class,
                inter.sdk.billing.enums.MoraCode.class,
                inter.sdk.billing.enums.OrderBy.class,
                inter.sdk.billing.enums.OrderType.class,
                inter.sdk.billing.enums.PersonType.class,
                inter.sdk.billing.enums.ReceivingOrigin.class,
                inter.sdk.commons.enums.EnvironmentEnum.class,
                inter.sdk.pix.enums.AgentModality.class,
                inter.sdk.pix.enums.BillingStatus.class,
                inter.sdk.pix.enums.DevolutionNature.class,
                inter.sdk.pix.enums.ImmediateBillingType.class,
                inter.sdk.pix.enums.PixBillingStatus.class
    );

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> params = new ArrayList<>();
        for (Class<?> c : ENUMS) {
            params.add(new Object[]{c});
        }
        return params;
    }

    private final Class<? extends Enum<?>> type;

    public EnumContractTest(Class<? extends Enum<?>> type) {
        this.type = type;
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void shouldRoundTripEveryConstant() {
        Enum<?>[] constants = type.getEnumConstants();
        assertTrue(constants.length > 0);
        for (Enum<?> constant : constants) {
            assertSame(constant, Enum.valueOf((Class) type, constant.name()));
            assertEquals(constant.name(), constant.toString());
        }
    }
}
