package inter.sdk.commons.utils;

import inter.sdk.commons.TestFixtures;
import inter.sdk.commons.enums.EnvironmentEnum;
import org.junit.Test;

import static inter.sdk.commons.structures.Constants.URL_BANKING_BALANCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class UrlUtilsTest {

    @Test
    public void shouldPrefixPathWithEnvironmentBaseUrl() {
        assertEquals("https://cdpj-sandbox.partners.uatinter.co/banking/v2/saldo",
                UrlUtils.buildUrl(TestFixtures.config(), URL_BANKING_BALANCE));
        assertEquals("https://cdpj.partners.bancointer.com.br/x",
                UrlUtils.buildUrl(TestFixtures.configBuilder().environment(EnvironmentEnum.PRODUCTION).build(), "/x"));
        assertEquals("https://cdpj.partners.uatbi.com.br/y",
                UrlUtils.buildUrl(TestFixtures.configBuilder().environment(EnvironmentEnum.UAT).build(), "/y"));
    }

    @Test
    public void shouldBeInstantiable() {
        assertNotNull(new UrlUtils());
    }
}
