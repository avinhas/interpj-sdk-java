package inter.sdk.commons.enums;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EnvironmentEnumTest {

    @Test
    public void shouldExposeLabelAndUrlBase() {
        assertEquals("PRODUCTION", EnvironmentEnum.PRODUCTION.getLabel());
        assertEquals("https://cdpj.partners.bancointer.com.br", EnvironmentEnum.PRODUCTION.getUrlBase());
        assertEquals("UAT", EnvironmentEnum.UAT.getLabel());
        assertEquals("https://cdpj.partners.uatbi.com.br", EnvironmentEnum.UAT.getUrlBase());
        assertEquals("SANDBOX", EnvironmentEnum.SANDBOX.getLabel());
        assertEquals("https://cdpj-sandbox.partners.uatinter.co", EnvironmentEnum.SANDBOX.getUrlBase());
        assertEquals(3, EnvironmentEnum.values().length);
        assertEquals(EnvironmentEnum.UAT, EnvironmentEnum.valueOf("UAT"));
    }
}
