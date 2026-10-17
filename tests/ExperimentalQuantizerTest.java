package dev.inknfclab;

import java.util.Arrays;

public final class ExperimentalQuantizerTest {
    public static void main(String[] args) {
        int[] pixels = new int[64 * 48];
        for (int index = 0; index < 4; index++) {
            Arrays.fill(pixels, ImageQuantizer.COLORS[index]);
            for (byte code : ExperimentalQuantizer.quantize(pixels, 64, 48, 0, 0, 0, .85)) require(code == index);
        }
        for (int index = 0; index < pixels.length; index++) {
            int gray = index % 256;
            pixels[index] = 0xff000000 | gray << 16 | gray << 8 | gray;
        }
        for (byte code : ExperimentalQuantizer.quantize(pixels, 64, 48, 0, 0, 0, 1)) require(code <= 1);
        for (int color : new int[]{0xff0000ff, 0xff00ffff, 0x00000000}) {
            Arrays.fill(pixels, color);
            for (byte code : ExperimentalQuantizer.quantize(pixels, 64, 48, 0, 0, 0, .85)) {
                require(code <= 1);
                if (color == 0) require(code == 1);
            }
        }
        int[] fixture = ClassicAlgorithmTest.fixture();
        int[] copy = fixture.clone();
        byte[] experimental = ExperimentalQuantizer.quantize(fixture, 97, 63, 0, 0, 0, .85);
        require(Arrays.equals(fixture, copy));
        require(Arrays.equals(experimental, ExperimentalQuantizer.quantize(fixture, 97, 63, 0, 0, 0, .85)));
        require(!Arrays.equals(experimental, ImageQuantizer.quantize(fixture, 97, 63, 0, 0, 0, .85)));
        for (byte code : experimental) require(code >= 0 && code <= 3);
        for (byte code : ExperimentalQuantizer.quantize(fixture, 97, 63, 2, 11, 24, .85)) require(code <= 1);
        for (int width = 1; width <= 2; width++)
            require(ExperimentalQuantizer.quantize(new int[width * 3], width, 3, 0, 0, 0, 1).length == width * 3);
        reject(() -> ExperimentalQuantizer.quantize(pixels, 1, 1, 0, 0, 0, 1));
        reject(() -> ExperimentalQuantizer.quantize(pixels, 64, 48, 3, 0, 0, 1));
        reject(() -> ExperimentalQuantizer.quantize(pixels, 64, 48, 0, 0, 0, Double.NaN));
        System.out.println("Experimental quantizer tests passed");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid experimental setting rejection");
    }
}
