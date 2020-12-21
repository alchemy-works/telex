package telex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TelexTest {

    @Test
    public void test() {
        var telex = new Telex("{token}");
        assertNotNull(telex);
    }
}
