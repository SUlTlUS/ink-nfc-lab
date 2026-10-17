package dev.inknfclab;

import java.util.Arrays;

public final class AlgorithmCatalogTest {
    public static void main(String[] args) {
        int[] fixture = ClassicAlgorithmTest.fixture();
        require(Arrays.equals(AlgorithmCatalog.quantize(fixture, 97, 63, 0, 0, 0, .85, 0),
                ImageQuantizer.quantize(fixture, 97, 63, 0, 0, 0, .85)));
        require(Arrays.equals(AlgorithmCatalog.quantize(fixture, 97, 63, 0, 0, 0, .85, 1),
                ExperimentalQuantizer.quantize(fixture, 97, 63, 0, 0, 0, .85)));
        for (int algorithm = 0; algorithm < AlgorithmCatalog.COUNT; algorithm++) {
            int[] pixels = fixture.clone();
            byte[] first = AlgorithmCatalog.quantize(pixels, 97, 63, 0, 0, 0, .85, algorithm);
            require(Arrays.equals(first, AlgorithmCatalog.quantize(pixels, 97, 63, 0, 0, 0, .85, algorithm)));
            require(Arrays.equals(pixels, fixture));
            for (byte code : first) require(code >= 0 && code <= 3);
            for (byte code : AlgorithmCatalog.quantize(pixels, 97, 63, 2, 0, 0, .85, algorithm)) require(code <= 1);
            for (int color = 0; color < 4; color++) {
                int[] solid = new int[15];
                Arrays.fill(solid, ImageQuantizer.COLORS[color]);
                for (byte code : AlgorithmCatalog.quantize(solid, 3, 5, 0, 0, 0, 1, algorithm)) require(code == color);
            }
            for (int width = 1; width < 4; width++) {
                int[] transparent = new int[width * 5];
                for (byte code : AlgorithmCatalog.quantize(transparent, width, 5, 0, 0, 0, 1, algorithm)) require(code == 1);
            }
            require(AlgorithmCatalog.name(algorithm).length() > 0);
        }
        int[] gray = new int[16 * 16];
        Arrays.fill(gray, 0xff808080);
        byte[] bayer = AlgorithmCatalog.quantize(gray, 16, 16, 0, 0, 0, 1, AlgorithmCatalog.BAYER);
        int white = 0;
        for (int row = 0; row < 16; row++) for (int column = 0; column < 16; column++) {
            require(bayer[row * 16 + column] == bayer[(row & 7) * 16 + (column & 7)]);
            if (bayer[row * 16 + column] == 1) white++;
        }
        require(white >= 120 && white <= 136);
        require(Arrays.equals(AlgorithmCatalog.quantize(gray, 16, 16, 0, 0, 0, 0, AlgorithmCatalog.BAYER),
                AlgorithmCatalog.quantize(gray, 16, 16, 0, 0, 0, 1, AlgorithmCatalog.CLEAN)));
        int[] fullFrame = new int[ImageCodec.PIXEL_COUNT];
        for (int index = 0; index < fullFrame.length; index++) fullFrame[index] = ImageQuantizer.COLORS[(index / ImageCodec.WIDTH) & 3];
        for (int algorithm = 0; algorithm < AlgorithmCatalog.COUNT; algorithm++) {
            byte[] packed = ImageCodec.pack(AlgorithmCatalog.quantize(fullFrame, ImageCodec.WIDTH, ImageCodec.HEIGHT, 0, 0, 0, .85, algorithm));
            require(packed.length == ImageCodec.PACKED_LENGTH);
            for (byte value : packed) require((value & 255) == 0xe4);
        }
        reject(() -> AlgorithmCatalog.name(7));
        reject(() -> AlgorithmCatalog.quantize(gray, 16, 16, 0, 0, 0, 1, -1));
        reject(() -> ResearchQuantizer.quantize(gray, 16, 16, 0, 0, 0, Double.NaN, 2));
        System.out.println("Seven algorithm catalog tests passed");
    }
    private static void require(boolean condition) { if (!condition) throw new AssertionError(); }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid algorithm rejection");
    }
}
