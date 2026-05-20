import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * One-shot generator for the PaintBrush item sheet (items.png).
 *
 * Produces a 256x256 sheet (16x16 grid of 16px icons). Row 0 holds the 16 brush
 * icons, one per dye colour; ItemPaintBrush.getIconFromDamage(meta) indexes into it.
 * Run once with the bundled JDK: `java tools/GenTexture.java`.
 */
public class GenTexture {

    // Diagonal paintbrush, handle top-right, bristle tuft bottom-left.
    static final String[] ART = {
        ".............KK.",
        "............KLDK",
        "...........KLDK.",
        "..........KLDK..",
        ".........KLDK...",
        "........KLDK....",
        ".......KLDK.....",
        "......KLDK......",
        ".....KFGK.......",
        "....KFGK........",
        "...KFGK.........",
        ".KHBBK..........",
        "KHBBSK..........",
        "KHBSK...........",
        "KBSK............",
        ".KK.............",
    };

    // Dye colours, indexed 0..15 (matches vanilla dye damage / IC2 paint index).
    static final int[] DYE = {
        0x1D1D21, 0xB02E26, 0x5E7C16, 0x835432,
        0x3C44AA, 0x8932B8, 0x169C9C, 0x9D9D97,
        0x474F52, 0xF38BAA, 0x80C71F, 0xFED83D,
        0x3AB3DA, 0xC74EBD, 0xF9801D, 0xF9FFFE,
    };

    static int lerp(int rgb, int target, double t) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int tr = (target >> 16) & 0xFF, tg = (target >> 8) & 0xFF, tb = target & 0xFF;
        r = (int) (r + (tr - r) * t);
        g = (int) (g + (tg - g) * t);
        b = (int) (b + (tb - b) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static void main(String[] a) throws Exception {
        BufferedImage sheet = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);

        for (int color = 0; color < 16; color++) {
            int base = 0xFF000000 | DYE[color];
            int hi   = lerp(DYE[color], 0xFFFFFF, 0.45);
            int sh   = lerp(DYE[color], 0x000000, 0.40);
            int ox = color * 16;

            for (int y = 0; y < 16; y++) {
                String row = ART[y];
                for (int x = 0; x < 16; x++) {
                    int argb;
                    switch (row.charAt(x)) {
                        case 'K': argb = 0xFF2B2118; break; // outline
                        case 'L': argb = 0xFFC8964B; break; // handle light
                        case 'D': argb = 0xFF8A5A2B; break; // handle dark
                        case 'F': argb = 0xFFD8D8E0; break; // ferrule light
                        case 'G': argb = 0xFF8E8E9A; break; // ferrule dark
                        case 'B': argb = base;       break; // bristle base
                        case 'H': argb = hi;         break; // bristle highlight
                        case 'S': argb = sh;         break; // bristle shadow
                        default:  argb = 0x00000000;        // transparent
                    }
                    sheet.setRGB(ox + x, y, argb);
                }
            }
        }

        File out = new File("src/main/resources/mods/paintbrush/textures/items/items.png");
        ImageIO.write(sheet, "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }
}
