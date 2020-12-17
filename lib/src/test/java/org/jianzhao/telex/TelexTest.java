package org.jianzhao.telex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelexTest {

    @Test
    public void test() {
        Telex telex = new Telex("token");
        assertNotNull(telex);
    }
}
