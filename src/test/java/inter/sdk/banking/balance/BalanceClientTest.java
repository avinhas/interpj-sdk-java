package inter.sdk.banking.balance;

import inter.sdk.banking.models.Balance;
import inter.sdk.commons.ClientTestSupport;
import inter.sdk.commons.exceptions.ClientException;
import inter.sdk.commons.models.Error;
import inter.sdk.commons.utils.HttpUtils;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

public class BalanceClientTest extends ClientTestSupport {

    private static final String URL = BASE + "/banking/v2/saldo";
    private final BalanceClient client = new BalanceClient();

    @Test
    public void shouldRetrieveBalanceWithoutDate() throws Exception {
        onGet(URL, "extrato.read", "{\"disponivel\":100.50,\"bloqueadoCheque\":1,\"limite\":10}");

        Balance balance = client.retrieve_balance(config, null);

        assertEquals(new BigDecimal("100.50"), balance.getAvailable());
        assertEquals(BigDecimal.ONE, balance.getCheckBlocked());
        assertEquals(BigDecimal.TEN, balance.getLimit());
        assertNull(balance.getJudiciallyBlocked());
        verifyGet(URL, "extrato.read", "Error retrieving balance");
    }

    @Test
    public void shouldAppendBalanceDateQueryParam() throws Exception {
        onGet(URL + "?dataSaldo=2024-05-01", "extrato.read", "{\"disponivel\":1}");

        Balance balance = client.retrieve_balance(config, "2024-05-01");

        assertEquals(BigDecimal.ONE, balance.getAvailable());
        verifyGet(URL + "?dataSaldo=2024-05-01", "extrato.read", "Error retrieving balance");
    }

    @Test
    public void shouldWrapMalformedJson() {
        onGet(URL, "extrato.read", NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieve_balance(config, null));
    }

    @Test
    public void shouldPropagateHttpErrors() {
        ClientException boom = new ClientException("Error retrieving balance", Error.builder().title("403").build());
        http.when(() -> HttpUtils.callGet(eq(config), eq(URL), eq("extrato.read"), anyString())).thenThrow(boom);
        assertPropagates(boom, () -> client.retrieve_balance(config, null));
    }
}
