package dev.inknfclab.thn31;

/** The session hook also checks the live JNI address and machine-code prefix. */
public final class Thn31Profile {
    public static boolean supported(String model, String chip, int sdk) {
        return "PLQ110".equals(model) && "THN31".equals(chip) && sdk == 37;
    }
    private Thn31Profile() {}
}
