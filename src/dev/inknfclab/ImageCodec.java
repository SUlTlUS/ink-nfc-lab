package dev.inknfclab;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class ImageCodec {
    public static final int WIDTH = 552;
    public static final int HEIGHT = 768;
    public static final int PIXEL_COUNT = WIDTH * HEIGHT;
    public static final int PACKED_LENGTH = 105984;
    public static final int BLOCK_SIZE = 2000;
    public static final int CHUNK_SIZE = 250;

    public static final class PreparedImage {
        public final Bitmap preview;
        public final Bitmap originalPreview;
        public final byte[] packed;
        public final boolean experimental;
        public final int algorithm;

        private PreparedImage(Bitmap preview, Bitmap originalPreview, byte[] packed, int algorithm) {
            this.preview = preview;
            this.originalPreview = originalPreview;
            this.packed = packed;
            this.experimental = algorithm != AlgorithmCatalog.CLASSIC;
            this.algorithm = algorithm;
        }
    }

    public static final class Packet {
        public final byte[] apdu;
        public final int block;
        public final int subpacket;

        private Packet(byte[] apdu, int block, int subpacket) {
            this.apdu = apdu;
            this.block = block;
            this.subpacket = subpacket;
        }
    }

    public static PreparedImage prepare(Bitmap source, boolean cover, boolean dither) {
        return prepare(source, cover, 0, ImageQuantizer.PHOTO, 0, 0, dither ? .85 : 0);
    }

    public static PreparedImage prepare(Bitmap source, boolean cover, int rotation, int mode,
                                        int brightness, int contrast, double strength) {
        return prepare(source, cover, rotation, mode, brightness, contrast, strength, false);
    }

    public static PreparedImage prepare(Bitmap source, boolean cover, int rotation, int mode,
                                        int brightness, int contrast, double strength, boolean experimental) {
        return prepare(source, cover, rotation, mode, brightness, contrast, strength,
                experimental ? AlgorithmCatalog.ATKINSON : AlgorithmCatalog.CLASSIC);
    }

    public static PreparedImage prepare(Bitmap source, boolean cover, int rotation, int mode,
                                        int brightness, int contrast, double strength, int algorithm) {
        AlgorithmCatalog.validate(algorithm);
        if (source == null || source.getWidth() < 1 || source.getHeight() < 1)
            throw new IllegalArgumentException("无效图片");
        Bitmap oriented = source;
        if (rotation % 360 != 0) {
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(rotation);
            oriented = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        }
        Bitmap scaled = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(scaled);
        canvas.drawColor(android.graphics.Color.WHITE);
        float scale = cover
                ? Math.max((float) WIDTH / oriented.getWidth(), (float) HEIGHT / oriented.getHeight())
                : Math.min((float) WIDTH / oriented.getWidth(), (float) HEIGHT / oriented.getHeight());
        float drawWidth = oriented.getWidth() * scale;
        float drawHeight = oriented.getHeight() * scale;
        android.graphics.RectF destination = new android.graphics.RectF(
                (WIDTH - drawWidth) / 2f, (HEIGHT - drawHeight) / 2f,
                (WIDTH + drawWidth) / 2f, (HEIGHT + drawHeight) / 2f);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(oriented, null, destination, paint);
        if (oriented != source) oriented.recycle();
        int[] pixels = new int[PIXEL_COUNT];
        scaled.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        byte[] codes = AlgorithmCatalog.quantize(pixels, WIDTH, HEIGHT, mode, brightness, contrast, strength, algorithm);
        byte[] packed = pack(codes);
        int[] previewPixels = new int[PIXEL_COUNT];
        for (int index = 0; index < codes.length; index++) {
            previewPixels[index] = ImageQuantizer.COLORS[codes[index] & 3];
        }
        Bitmap preview = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        preview.setPixels(previewPixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        return new PreparedImage(preview, scaled, packed, algorithm);
    }

    public static byte[] pack(byte[] codes) {
        if (codes == null || codes.length != PIXEL_COUNT) throw new IllegalArgumentException("像素码长度必须为423936字节");
        byte[] result = new byte[PACKED_LENGTH];
        int output = 0;
        for (int x = WIDTH - 1; x >= 0; x--) {
            for (int y = HEIGHT - 1; y >= 0; y -= 4) {
                result[output++] = (byte) (((codes[(y) * WIDTH + x] & 3) << 6)
                        | ((codes[(y - 1) * WIDTH + x] & 3) << 4)
                        | ((codes[(y - 2) * WIDTH + x] & 3) << 2)
                        | (codes[(y - 3) * WIDTH + x] & 3));
            }
        }
        return result;
    }

    public static byte[] lzoLiteralBlock(byte[] input) {
        if (input == null || input.length < 1 || input.length > BLOCK_SIZE)
            throw new IllegalArgumentException("LZO块长度超出范围");
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length + 16);
        if (input.length <= 238) {
            output.write(input.length + 17);
        } else {
            output.write(0);
            int remainder = input.length - 18;
            while (remainder > 255) {
                output.write(0);
                remainder -= 255;
            }
            output.write(remainder);
        }
        output.write(input, 0, input.length);
        output.write(0x11);
        output.write(0);
        output.write(0);
        return output.toByteArray();
    }

    public static List<Packet> packets(byte[] packed) {
        return packets(packed, CHUNK_SIZE);
    }

    public static List<Packet> packets(byte[] packed, int chunkSize) {
        if (packed == null || packed.length != PACKED_LENGTH) throw new IllegalArgumentException("点阵长度必须为105984字节");
        if (chunkSize < 1 || chunkSize > CHUNK_SIZE) throw new IllegalArgumentException("ISO-DEP分片长度超出范围");
        List<Packet> result = new ArrayList<>();
        for (int block = 0, start = 0; start < packed.length; block++, start += BLOCK_SIZE) {
            int length = Math.min(BLOCK_SIZE, packed.length - start);
            byte[] raw = java.util.Arrays.copyOfRange(packed, start, start + length);
            byte[] compressed = lzoLiteralBlock(raw);
            int count = (compressed.length + chunkSize - 1) / chunkSize;
            if (count > 256) throw new IllegalArgumentException("此设备分片过小，超过单块256包上限，拒绝传输");
            for (int index = 0, offset = 0; offset < compressed.length; index++, offset += chunkSize) {
                int partLength = Math.min(chunkSize, compressed.length - offset);
                byte[] apdu = new byte[7 + partLength];
                apdu[0] = (byte) 0xf0;
                apdu[1] = (byte) 0xd3;
                apdu[2] = 0;
                apdu[3] = (byte) (index == count - 1 ? 1 : 0);
                apdu[4] = (byte) (partLength + 2);
                apdu[5] = (byte) block;
                apdu[6] = (byte) index;
                System.arraycopy(compressed, offset, apdu, 7, partLength);
                result.add(new Packet(apdu, block, index));
            }
        }
        return result;
    }

    private ImageCodec() {}
}
