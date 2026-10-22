package dev.inknfclab;

import java.util.Locale;

public final class NfcVendor {
    public enum Kind { NXP, ST, UNKNOWN }

    public static Kind identify(String activePackagePath, String chipProperties) {
        String path = activePackagePath == null ? "" : activePackagePath.toLowerCase(Locale.ROOT);
        String properties = chipProperties == null ? "" : chipProperties.toLowerCase(Locale.ROOT);
        if (path.contains("nfc_st") || path.contains("st54")) return Kind.ST;
        if (path.contains("nfc_nxp")) return Kind.NXP;
        boolean st = properties.contains("st54") || properties.contains("st21") || properties.contains("st25");
        boolean nxp = properties.contains("pn553") || properties.contains("pn557")
                || properties.contains("pn560") || properties.contains("sn100")
                || properties.contains("sn110") || properties.contains("sn220")
                || properties.contains("=nxp") || properties.contains("=nq");
        if (st == nxp) return Kind.UNKNOWN;
        return st ? Kind.ST : Kind.NXP;
    }

    private NfcVendor() {}
}
