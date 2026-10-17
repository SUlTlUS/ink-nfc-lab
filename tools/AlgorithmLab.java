import dev.inknfclab.AlgorithmCatalog;
import dev.inknfclab.ImageQuantizer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

public final class AlgorithmLab {
    private static final int WIDTH = 552, HEIGHT = 768;

    public static void main(String[] args) throws Exception {
        File output = new File(args[0]);
        new File(output, "sources").mkdirs();
        new File(output, "outputs").mkdirs();
        List<String> names = new ArrayList<>();
        List<BufferedImage> fixtures = new ArrayList<>();
        names.add("01-calibration"); fixtures.add(fit(ImageIO.read(new File(args[1]))));
        names.add("02-portrait"); fixtures.add(fit(ImageIO.read(new File(args[2]))));
        names.add("03-text-lines"); fixtures.add(textFixture());
        names.add("04-skin-neutral"); fixtures.add(patches());
        names.add("05-shadow-texture"); fixtures.add(texture());
        StringBuilder csv = new StringBuilder("case,algorithm,name,host_ms,block8_rgb_rmse,neutral_chromatic_fraction,black_fraction,white_fraction,yellow_fraction,red_fraction\n");
        StringBuilder html = new StringBuilder("<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>Ink Studio 算法实验室</title><style>body{font:15px system-ui;background:#10171d;color:#e9f3f7;margin:24px}a{color:#71e1c5}h1{font-size:28px}section{margin:36px 0}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:16px}figure{margin:0;background:#1c2730;padding:12px;border-radius:12px}img{width:100%;height:auto;image-rendering:auto}figcaption{padding:10px 0;line-height:1.6}p{max-width:1000px;line-height:1.7}</style><h1>Ink Studio · 七种算法对照</h1><p>经典算法保持默认。所有图片使用同一输入、552×768、亮度0、对比度0、照片模式、抖动0.85（纯色算法关闭抖动）。点击图片查看原始像素。这里是软件预览，不是墨水屏实拍；屏幕最佳效果仍须实屏 A/B。</p><p>8×8 块均值 RGB RMSE 只是低频保真代理，不能证明最佳观感。无抖动可能指标较差却更适合文字。耗时是这台 Mac 的单次测量，不是手机速度。<a href='metrics.csv'>下载原始指标</a></p>");
        AlgorithmCatalog.quantize(fixtures.get(0).getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH), WIDTH, HEIGHT, 0, 0, 0, .85, 0);
        for (int fixture = 0; fixture < fixtures.size(); fixture++) {
            String name = names.get(fixture);
            BufferedImage source = fixtures.get(fixture);
            ImageIO.write(source, "png", new File(output, "sources/" + name + ".png"));
            int[] pixels = source.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);
            BufferedImage sheet = new BufferedImage(WIDTH * 2, (HEIGHT / 2 + 44) * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D contact = sheet.createGraphics();
            contact.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            contact.setColor(new Color(16, 23, 29)); contact.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            tile(contact, source, 0, "SOURCE");
            html.append("<section><h2>").append(name).append("</h2><div class='grid'><figure><a href='sources/").append(name)
                    .append(".png'><img loading='lazy' src='sources/").append(name).append(".png'></a><figcaption>原始输入</figcaption></figure>");
            for (int algorithm = 0; algorithm < AlgorithmCatalog.COUNT; algorithm++) {
                long started = System.nanoTime();
                byte[] codes = AlgorithmCatalog.quantize(pixels, WIDTH, HEIGHT, 0, 0, 0, .85, algorithm);
                double milliseconds = (System.nanoTime() - started) / 1000000.0;
                BufferedImage rendered = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                for (int index = 0; index < codes.length; index++) rendered.setRGB(index % WIDTH, index / WIDTH, ImageQuantizer.COLORS[codes[index]]);
                String path = "outputs/" + name + "-a" + algorithm + ".png";
                ImageIO.write(rendered, "png", new File(output, path));
                double error = blockError(source, rendered), neutral = neutralTint(pixels, codes);
                int[] counts = new int[4]; for (byte code : codes) counts[code]++;
                csv.append(String.format(Locale.ROOT, "%s,%d,%s,%.3f,%.5f,%.7f,%.7f,%.7f,%.7f,%.7f%n", name, algorithm,
                        AlgorithmCatalog.name(algorithm), milliseconds, error, neutral,
                        counts[0] / (double) codes.length, counts[1] / (double) codes.length,
                        counts[2] / (double) codes.length, counts[3] / (double) codes.length));
                html.append("<figure><a href='").append(path).append("'><img loading='lazy' src='").append(path)
                        .append("'></a><figcaption>").append(algorithm).append(" · ").append(AlgorithmCatalog.name(algorithm))
                        .append(String.format(Locale.ROOT, "<br>块误差 %.2f · %.1fms<br>中性区域彩色噪点 %.2f%%", error, milliseconds, neutral * 100))
                        .append("</figcaption></figure>");
                tile(contact, rendered, algorithm + 1, algorithm + " " + AlgorithmCatalog.name(algorithm));
            }
            html.append("</div></section>");
            contact.dispose();
            ImageIO.write(sheet, "png", new File(output, name + "-contact.png"));
            System.out.println("Completed " + name + " / 7 algorithms");
        }
        html.append("<p>人像来源：NASA / scikit-image astronaut，public domain。其余测试图为程序生成；肤色面板不是色卡校准。<a href='https://scikit-image.org/docs/stable/api/skimage.data.html#skimage.data.astronaut'>来源说明</a></p>");
        Files.write(new File(output, "index.html").toPath(), html.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(new File(output, "metrics.csv").toPath(), csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static BufferedImage fit(BufferedImage source) {
        if (source == null) throw new IllegalArgumentException("Missing image fixture");
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D drawing = result.createGraphics();
        drawing.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double scale = Math.max(WIDTH / (double) source.getWidth(), HEIGHT / (double) source.getHeight());
        int width = (int) Math.round(source.getWidth() * scale), height = (int) Math.round(source.getHeight() * scale);
        drawing.drawImage(source, (WIDTH - width) / 2, (HEIGHT - height) / 2, width, height, null);
        drawing.dispose(); return result;
    }

    private static BufferedImage textFixture() {
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D drawing = result.createGraphics();
        drawing.setColor(Color.WHITE); drawing.fillRect(0, 0, WIDTH, HEIGHT);
        drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int baseline = 32;
        for (int size : new int[]{8, 12, 16, 20, 28, 40}) {
            drawing.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
            drawing.setColor(Color.BLACK); drawing.drawString(size + "px Ink Studio 0123456789", 24, baseline);
            baseline += size + 16;
        }
        drawing.setFont(new Font("PingFang SC", Font.PLAIN, 24));
        drawing.drawString("中文细节 · 墨水屏 · 黑白红黄", 24, baseline + 10);
        for (int width = 1; width <= 5; width++) {
            drawing.fillRect(24, 390 + width * 22, 500, width);
            drawing.fillRect(24 + width * 32, 540, width, 160);
        }
        drawing.setColor(new Color(200, 0, 0)); drawing.fillRect(260, 545, 250, 50);
        drawing.setColor(new Color(245, 205, 0)); drawing.fillRect(260, 615, 250, 50);
        drawing.setColor(Color.WHITE); drawing.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
        drawing.drawString("REVERSED TYPE", 270, 578);
        drawing.setColor(Color.BLACK); drawing.drawString("INK / POSTER", 270, 649);
        drawing.dispose(); return result;
    }

    private static BufferedImage patches() {
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Color[] tones = {new Color(55, 30, 23), new Color(101, 65, 45), new Color(158, 111, 82),
                new Color(202, 157, 123), new Color(237, 197, 164), new Color(250, 225, 203)};
        for (int row = 0; row < HEIGHT; row++) for (int column = 0; column < WIDTH; column++) {
            Color color;
            if (row < 384) {
                Color tone = tones[column * tones.length / WIDTH];
                double gain = .45 + row / 384.0 * .75;
                color = new Color(limit(tone.getRed() * gain), limit(tone.getGreen() * gain), limit(tone.getBlue() * gain));
            } else if (row < 576) {
                int gray = column * 255 / (WIDTH - 1); color = new Color(gray, gray, gray);
            } else {
                double hue = column / (double) (WIDTH - 1);
                color = Color.getHSBColor((float) hue, (float) ((row - 576) / 191.0), .8f);
            }
            result.setRGB(column, row, color.getRGB());
        }
        return result;
    }

    private static BufferedImage texture() {
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < HEIGHT; row++) for (int column = 0; column < WIDTH; column++) {
            double shade = column / (double) WIDTH;
            double detail = Math.sin(column * .47) * Math.cos(row * .53) * 18 + Math.sin(column * .09 + row * .11) * 9;
            Color color = row < 256 ? new Color(limit(50 + shade * 130), limit(100 + shade * 120), limit(150 + shade * 90))
                    : row < 512 ? new Color(limit(18 + shade * 80 + detail), limit(30 + shade * 100 + detail), limit(16 + shade * 60 + detail))
                    : new Color(limit(10 + shade * 200 + detail), limit(10 + shade * 200 + detail), limit(10 + shade * 200 + detail));
            result.setRGB(column, row, color.getRGB());
        }
        return result;
    }

    private static void tile(Graphics2D drawing, BufferedImage image, int index, String label) {
        int left = index % 4 * 276, top = index / 4 * (384 + 44);
        drawing.drawImage(image, left, top + 44, 276, 384, null);
        drawing.setColor(Color.WHITE); drawing.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        drawing.drawString(label, left + 8, top + 27);
    }

    private static double blockError(BufferedImage source, BufferedImage rendered) {
        double sum = 0; int count = 0;
        for (int top = 0; top < HEIGHT; top += 8) for (int left = 0; left < WIDTH; left += 8) {
            for (int shift : new int[]{16, 8, 0}) {
                double first = 0, second = 0; int pixels = 0;
                for (int row = top; row < Math.min(top + 8, HEIGHT); row++)
                    for (int column = left; column < Math.min(left + 8, WIDTH); column++) {
                        first += (source.getRGB(column, row) >>> shift) & 255;
                        second += (rendered.getRGB(column, row) >>> shift) & 255; pixels++;
                    }
                sum += Math.pow((first - second) / pixels, 2); count++;
            }
        }
        return Math.sqrt(sum / count);
    }

    private static double neutralTint(int[] pixels, byte[] codes) {
        int neutral = 0, tinted = 0;
        for (int index = 0; index < pixels.length; index++) {
            int red = (pixels[index] >>> 16) & 255, green = (pixels[index] >>> 8) & 255, blue = pixels[index] & 255;
            if (Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) < 8) {
                neutral++; if (codes[index] >= 2) tinted++;
            }
        }
        return neutral == 0 ? 0 : tinted / (double) neutral;
    }
    private static int limit(double value) { return (int) Math.max(0, Math.min(255, value)); }
}
