package inter.sdk.commons.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import inter.sdk.banking.models.Balance;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AbstractModelTest {

    private Balance model;

    @Before
    public void setUp() {
        model = new Balance();
    }

    @Test
    public void shouldStartWithEmptyAdditionalFields() {
        assertTrue(model.getAdditionalFields().isEmpty());
        assertEquals("Balance(super=AbstractModel[], available=null, checkBlocked=null, judiciallyBlocked=null, administrativelyBlocked=null, limit=null)", model.toString());
    }

    @Test
    public void shouldAddAdditionalFieldAndExposeDefensiveCopy() {
        model.setAdditionalField("foo", "bar");
        Map<String, String> copy = model.getAdditionalFields();
        assertEquals("bar", copy.get("foo"));
        copy.put("mutated", "x");
        assertFalse(model.getAdditionalFields().containsKey("mutated"));
    }

    @Test
    public void shouldReplaceAdditionalFieldsWithCopy() {
        Map<String, String> source = new HashMap<>();
        source.put("a", "1");
        model.setAdditionalFields(source);
        source.put("b", "2");
        assertEquals(1, model.getAdditionalFields().size());
        assertEquals("1", model.getAdditionalFields().get("a"));
    }

    @Test
    public void shouldBuildWithAdditionalFieldsViaSuperBuilder() {
        Map<String, String> extra = new HashMap<>();
        extra.put("k", "v");
        Balance built = Balance.builder().additionalFields(extra).available(BigDecimal.TEN).build();
        assertEquals("v", built.getAdditionalFields().get("k"));
        assertEquals(BigDecimal.TEN, built.getAvailable());
        assertTrue(Balance.builder().build().getAdditionalFields().isEmpty());
    }

    @Test
    public void shouldCompareAdditionalFieldsInEqualsAndHashCode() {
        Balance other = new Balance();
        assertEquals(model, other);
        assertEquals(model.hashCode(), other.hashCode());
        assertEquals(model, model);
        assertNotEquals(model, null);
        assertNotEquals(model, "string");

        model.setAdditionalField("a", "1");
        assertNotEquals(model, other);
        assertNotEquals(model.hashCode(), other.hashCode());

        other.setAdditionalField("b", "1");
        assertNotEquals(model, other);

        Balance sameKeyDifferentValue = new Balance();
        sameKeyDifferentValue.setAdditionalField("a", "2");
        assertNotEquals(model, sameKeyDifferentValue);

        Balance same = new Balance();
        same.setAdditionalField("a", "1");
        assertEquals(model, same);
        assertEquals(model.hashCode(), same.hashCode());

        Balance nullValue = new Balance();
        nullValue.setAdditionalField("a", null);
        Balance nullValue2 = new Balance();
        nullValue2.setAdditionalField("a", null);
        assertEquals(nullValue, nullValue2);
        assertEquals(nullValue.hashCode(), nullValue2.hashCode());
        assertNotEquals(nullValue, model);
        assertNotEquals(model, nullValue);
    }

    @Test
    public void shouldRenderAdditionalFieldsInToString() {
        model.setAdditionalField("a", "1");
        assertTrue(model.toString().contains("AbstractModel[a=1]"));
    }

    @Test
    public void shouldCaptureUnknownJsonPropertiesAndSerializeThemBack() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Balance parsed = mapper.readValue("{\"disponivel\":10,\"unknownField\":\"x\"}", Balance.class);
        assertEquals("x", parsed.getAdditionalFields().get("unknownField"));
        String json = mapper.writeValueAsString(parsed);
        assertTrue(json.contains("\"unknownField\":\"x\""));
        assertFalse(json.contains("additionalFields"));
    }
}
