package inter.sdk.pix.locations;

import inter.sdk.commons.ClientTestSupport;
import inter.sdk.pix.enums.ImmediateBillingType;
import inter.sdk.pix.models.Location;
import inter.sdk.pix.models.LocationPage;
import inter.sdk.pix.models.RetrieveLocationFilter;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocationClientTest extends ClientTestSupport {

    private static final String READ = "payloadlocation.read";
    private static final String WRITE = "payloadlocation.write";
    private static final String URL = BASE + "/pix/v2/loc";
    private static final String RANGE = "?inicio=2024-01-01&fim=2024-01-31";
    private static final String TWO_PAGES = "{\"parametros\":{\"paginacao\":{\"quantidadeDePaginas\":2}},\"loc\":[{\"id\":%d}]}";

    private final LocationClient client = new LocationClient();

    @Test
    public void shouldIncludeLocation() throws Exception {
        onPost(URL, WRITE, "{\"id\":7,\"tipoCob\":\"cob\",\"location\":\"pix.example/7\"}");

        Location location = client.includeLocation(config, ImmediateBillingType.cob);

        assertEquals(Long.valueOf(7), location.getId());
        assertEquals(ImmediateBillingType.cob, location.getBillingType());
        verifyPost(URL, WRITE, "Error including location", "\"tipoCob\" : \"cob\"");
    }

    @Test
    public void shouldWrapMalformedInclude() {
        onPost(URL, WRITE, NOT_JSON);
        assertWrapsParseFailure(() -> client.includeLocation(config, ImmediateBillingType.cobv));
    }

    @Test
    public void shouldRetrieveLocation() throws Exception {
        onGet(URL + "/7", READ, "{\"id\":7,\"txid\":\"tx\"}");

        Location location = client.retrieveLocation(config, "7");

        assertEquals("tx", location.getTxid());
        verifyGet(URL + "/7", READ, "Error retrieving location");
    }

    @Test
    public void shouldWrapMalformedRetrieve() {
        onGet(URL + "/7", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveLocation(config, "7"));
    }

    @Test
    public void shouldRetrievePageWithAllFilters() throws Exception {
        String url = URL + RANGE + "&paginacao.paginaAtual=1&paginacao.itensPorPagina=10&txIdPresente=true&tipoCob=cobv";
        onGet(url, READ, String.format(TWO_PAGES, 1));

        LocationPage page = client.retrieveLocationPage(config, "2024-01-01", "2024-01-31", 1, 10,
                RetrieveLocationFilter.builder().txIdPresent(true).billingType(ImmediateBillingType.cobv).build());

        assertEquals(2, page.getTotalPages());
        assertEquals(Long.valueOf(1), page.getLocations().get(0).getId());
        verifyGet(url, READ, "Error retrieving locations");
    }

    @Test
    public void shouldRetrievePageWithPartialFilter() throws Exception {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0&tipoCob=cob", READ, "{\"loc\":[]}");
        assertTrue(client.retrieveLocationPage(config, "2024-01-01", "2024-01-31", 0, null,
                RetrieveLocationFilter.builder().billingType(ImmediateBillingType.cob).build()).getLocations().isEmpty());

        onGet(URL + RANGE + "&paginacao.paginaAtual=0&txIdPresente=false", READ, "{\"parametros\":{},\"loc\":[]}");
        assertEquals(0, client.retrieveLocationPage(config, "2024-01-01", "2024-01-31", 0, null,
                RetrieveLocationFilter.builder().txIdPresent(false).build()).getTotalPages());
    }

    @Test
    public void shouldWalkAllPages() throws Exception {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, String.format(TWO_PAGES, 1));
        onGet(URL + RANGE + "&paginacao.paginaAtual=1", READ, String.format(TWO_PAGES, 2));

        List<Location> all = client.retrieveLocationInRange(config, "2024-01-01", "2024-01-31", null);

        assertEquals(2, all.size());
        assertEquals(Long.valueOf(2), all.get(1).getId());
    }

    @Test
    public void shouldWrapMalformedPage() {
        onGet(URL + RANGE + "&paginacao.paginaAtual=0", READ, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveLocationInRange(config, "2024-01-01", "2024-01-31", null));
    }

    @Test
    public void shouldUnlinkLocation() throws Exception {
        onDelete(URL + "/7/txid", WRITE, "{\"id\":7}");

        Location location = client.unlinkLocation(config, "7");

        assertEquals(Long.valueOf(7), location.getId());
        verifyDelete(URL + "/7/txid", WRITE, "Error unlinking location");
    }

    @Test
    public void shouldWrapMalformedUnlink() {
        onDelete(URL + "/7/txid", WRITE, NOT_JSON);
        assertWrapsParseFailure(() -> client.unlinkLocation(config, "7"));
    }
}
