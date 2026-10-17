package dev.inknfclab;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.FileOutputStream;

public final class AndroidImageSmoke {
    public static void main(String[] args) throws Exception {
        Bitmap source = BitmapFactory.decodeFile(args[0]);
        if (source == null) throw new AssertionError("Cannot decode fixture");
        for (int variant = 0; variant < AlgorithmCatalog.COUNT * 3; variant++) {
            int algorithm = variant / 3;
            int mode = variant % 3;
            long started = android.os.SystemClock.elapsedRealtime();
            ImageCodec.PreparedImage frame = ImageCodec.prepare(source, false, mode == 1 ? 90 : 0, mode, 0, 0,
                    mode == ImageQuantizer.ARTWORK ? 0 : .85, algorithm);
            if (frame.algorithm != algorithm || frame.experimental != (algorithm != 0)) throw new AssertionError("Algorithm flag mismatch");
            if (algorithm == 0) {
                ImageCodec.PreparedImage legacy = ImageCodec.prepare(source, false, mode == 1 ? 90 : 0, mode, 0, 0,
                        mode == ImageQuantizer.ARTWORK ? 0 : .85);
                if (!java.util.Arrays.equals(frame.packed, legacy.packed)) throw new AssertionError("Default no longer classic");
                legacy.preview.recycle();
                legacy.originalPreview.recycle();
            }
            if (frame.packed.length != ImageCodec.PACKED_LENGTH || frame.preview.getWidth() != 552
                    || frame.originalPreview.getHeight() != 768) throw new AssertionError("Invalid image dimensions");
            int[] preview = new int[ImageCodec.PIXEL_COUNT];
            frame.preview.getPixels(preview, 0, 552, 0, 0, 552, 768);
            for (int row = 0; row < 768; row++) {
                for (int column = 0; column < 552; column++) {
                    int packedIndex = (551 - column) * 192 + (767 - row) / 4;
                    int shift = (3 - (767 - row) % 4) * 2;
                    int code = (frame.packed[packedIndex] >>> shift) & 3;
                    if (preview[row * 552 + column] != ImageQuantizer.COLORS[code])
                        throw new AssertionError("Preview differs from payload");
                    if (mode == ImageQuantizer.MONO && code > 1) throw new AssertionError("Mono has chromatic pixels");
                }
            }
            if (mode == 0) {
                try (FileOutputStream output = new FileOutputStream(args[1] + (algorithm == 0 ? "" : ".a" + algorithm + ".png"))) {
                    frame.preview.compress(Bitmap.CompressFormat.PNG, 100, output);
                }
            }
            System.out.println("ALGORITHM=" + algorithm + " MODE=" + mode + " PREVIEW_PAYLOAD_MATCH=423936/423936 ELAPSED="
                    + (android.os.SystemClock.elapsedRealtime() - started) + "ms");
            frame.preview.recycle();
            frame.originalPreview.recycle();
        }
        source.recycle();
        System.out.println("Android image smoke test passed");
    }
}
