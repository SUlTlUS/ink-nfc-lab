package dev.inknfclab.thn31;

import static dev.inknfclab.thn31.TestSupport.*;

public final class Thn31ProfileTest {
    public static void main(String[] args) {
        assertTrue(Thn31Profile.supported("PLQ110", "THN31", 37));
        assertFalse(Thn31Profile.supported("PLQ110", "THN31", 36));
        assertFalse(Thn31Profile.supported("other", "THN31", 37));
        assertFalse(Thn31Profile.supported("PLQ110", "ST54L", 37));
        System.out.println("Thn31ProfileTest: 4 passed");
    }
}
