package inter.sdk.commons.structures;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConstantsTest {

    @Test
    public void shouldComposeUrlsFromBases() {
        assertEquals("/banking/v2/saldo", Constants.URL_BANKING_BALANCE);
        assertEquals("/banking/v2/extrato/completo", Constants.URL_BANKING_ENRICHED_STATEMENT);
        assertEquals("/pix/v2/webhook/callbacks", Constants.URL_PIX_WEBHOOK_CALLBACKS);
        assertEquals("/cobranca/v3/cobrancas/webhook/callbacks", Constants.URL_BILLING_WEBHOOK_CALLBACKS);
        assertEquals(30, Constants.DAYS_TO_EXPIRE);
    }

    @Test
    public void shouldBeExtensible() {
        assertNotNull(new Constants() {
        });
    }
}
