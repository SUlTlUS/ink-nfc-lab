package dev.inknfclab;

import java.security.MessageDigest;

public final class ClassicAlgorithmTest {
    public static void main(String[] args) throws Exception {
        int[] pixels = fixture();
        int[][] settings = {{0, 0, 0}, {1, 0, 0}, {2, 0, 0}, {0, 11, 24}};
        double[] strengths = {.85, 0, 1, .85};
        String[] expected = {
                "d9d08ca9f868779f57675f250dd26c3328740fdfa8ee51797fafbf30f2a0198c",
                "f4ff053bbed77d9155fe04f381da6cb7079e8ace997bac6e7bf08f50763fd6ab",
                "aa5bb37b716e58d7705cf877999856bc7e448f37ca6f12f562e3eb457d62f2ba",
                "21eea490852188adbd525a099c0c261a7abb3960c28ffba22564dee8816c61be"};
        for (int index = 0; index < settings.length; index++) {
            byte[] codes = ImageQuantizer.quantize(pixels, 97, 63, settings[index][0], settings[index][1], settings[index][2], strengths[index]);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codes);
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format("%02x", value & 255));
            if (!hex.toString().equals(expected[index])) throw new AssertionError("0.4.0 algorithm changed: " + index);
        }
        System.out.println("Classic 0.4.0 golden outputs unchanged");
    }

    static int[] fixture() {
        int[] pixels = new int[97 * 63];
        for (int index = 0; index < pixels.length; index++) {
            int column = index % 97, row = index / 97;
            pixels[index] = ((column * 19 + row * 23) & 255) << 24
                    | ((column * 17 + row * 3) & 255) << 16
                    | ((column * 5 + row * 13) & 255) << 8 | ((column * 11 + row * 7) & 255);
        }
        return pixels;
    }
}
