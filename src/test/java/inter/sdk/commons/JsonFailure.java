package inter.sdk.commons;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.mockito.MockedConstruction;

import java.io.Closeable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * Makes every {@code new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(..)} throw a
 * {@link JsonMappingException}, so the IOException catch blocks around request serialization can be exercised.
 */
public final class JsonFailure {

    public static final String MESSAGE = "simulated serialization failure";

    private JsonFailure() {
    }

    public static MockedConstruction<ObjectMapper> failingSerialization() {
        return mockConstruction(ObjectMapper.class, (mapper, context) -> {
            ObjectWriter writer = mock(ObjectWriter.class);
            when(mapper.writerWithDefaultPrettyPrinter()).thenReturn(writer);
            when(writer.writeValueAsString(any())).thenThrow(new JsonMappingException((Closeable) null, MESSAGE));
        });
    }
}
