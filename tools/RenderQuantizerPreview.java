import dev.inknfclab.ImageQuantizer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class RenderQuantizerPreview {
    public static void main(String[] args) throws Exception {
        int width = 552, height = 768;
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D drawing = source.createGraphics();
        drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawing.setColor(new Color(250, 247, 236));
        drawing.fillRect(0, 0, width, height);
        drawing.setColor(Color.BLACK);
        drawing.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        drawing.drawString("INK / FOUR COLORS", 28, 65);
        drawing.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 23));
        drawing.drawString("Fine type, neutral tones, gradients", 28, 105);
        for (int column = 0; column < width; column++) {
            int value = column * 255 / (width - 1);
            drawing.setColor(new Color(value, value, value));
            drawing.drawLine(column, 135, column, 275);
            drawing.setColor(Color.getHSBColor(column / (float) width, .8f, .88f));
            drawing.drawLine(column, 310, column, 465);
        }
        for (int row = 0; row < 190; row++) {
            int value = 30 + row;
            drawing.setColor(new Color(value, value, value));
            drawing.drawLine(25, 500 + row, 335, 500 + row);
        }
        drawing.setColor(new Color(200, 0, 0));
        drawing.fillOval(375, 520, 130, 130);
        drawing.setColor(new Color(245, 205, 0));
        drawing.fillRect(350, 660, 175, 60);
        drawing.setColor(Color.BLACK);
        drawing.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        drawing.drawString("TEXT 0123456789 / clean edges", 32, 735);
        drawing.dispose();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        BufferedImage comparison = new BufferedImage(width * 2, height + 54, BufferedImage.TYPE_INT_RGB);
        drawing = comparison.createGraphics();
        drawing.setColor(new Color(16, 23, 29));
        drawing.fillRect(0, 0, comparison.getWidth(), comparison.getHeight());
        drawing.setColor(Color.WHITE);
        drawing.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 23));
        drawing.drawString("SOURCE", 20, 36);
        drawing.drawString("PHOTO / SOFT DITHER", width + 20, 36);
        drawing.drawImage(source, 0, 54, null);
        long started = System.nanoTime();
        byte[] codes = ImageQuantizer.quantize(pixels, width, height, 0, 0, 0, .85);
        for (int index = 0; index < codes.length; index++)
            comparison.setRGB(width + index % width, 54 + index / width, ImageQuantizer.COLORS[codes[index]]);
        drawing.dispose();
        ImageIO.write(source, "png", new File(args[0]));
        ImageIO.write(comparison, "png", new File(args[1]));
        System.out.println("Rendered " + codes.length + " pixels in " + (System.nanoTime() - started) / 1000000 + " ms");
    }
}
