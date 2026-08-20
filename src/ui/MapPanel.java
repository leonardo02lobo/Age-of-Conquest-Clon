package ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import model.GameState;
import model.Nation;
import model.Province;

/**
 * Mapa clicable: dibuja cada provincia como un polígono coloreado por dueño,
 * con nombre, tropas, rey (♔) y fortificación (⛨). Al seleccionar una
 * provincia propia se resaltan sus destinos alcanzables (tierra y salto naval).
 * Si el escenario no trae polígonos, genera una cuadrícula automática.
 */
public class MapPanel extends JPanel {

    /** Notificación de clic sobre una provincia (o null al hacer clic en el vacío). */
    public interface Listener {
        void provinceClicked(Province province);
    }

    private static final Color OCEAN = new Color(22, 44, 74);
    private static final Color SEA = new Color(38, 74, 118);
    private static final Color NEUTRAL = new Color(150, 144, 130);
    private static final Color[] PALETTE = {
            new Color(178, 52, 43),   // rojo
            new Color(112, 66, 148),  // púrpura
            new Color(46, 133, 64),   // verde
            new Color(199, 146, 33),  // dorado
            new Color(38, 128, 140),  // turquesa
            new Color(170, 84, 33),   // naranja
    };

    private final GameState state;
    private final Listener listener;
    private final Map<String, Polygon> polygons = new LinkedHashMap<>();
    private final Map<String, Color> nationColors = new HashMap<>();
    private final int mapWidth;
    private final int mapHeight;

    private String selectedId;
    private final Set<String> highlightedIds = new HashSet<>();

    public MapPanel(GameState state, Listener listener) {
        this.state = state;
        this.listener = listener;
        buildPolygons();
        Rectangle bounds = new Rectangle(0, 0, 100, 100);
        for (Polygon polygon : polygons.values()) {
            bounds = bounds.union(polygon.getBounds());
        }
        mapWidth = bounds.x + bounds.width + 40;
        mapHeight = bounds.y + bounds.height + 40;

        int i = 0;
        for (Nation nation : state.nations()) {
            nationColors.put(nation.id(), PALETTE[i++ % PALETTE.length]);
        }

        setToolTipText("");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                listener.provinceClicked(provinceAt(e.getPoint()));
            }
        });
    }

    private void buildPolygons() {
        int missing = 0;
        for (Province province : state.provinces()) {
            int[] coords = province.polygon();
            Polygon polygon = new Polygon();
            if (coords != null) {
                for (int i = 0; i < coords.length; i += 2) {
                    polygon.addPoint(coords[i], coords[i + 1]);
                }
            } else {
                // Cuadrícula de reserva para escenarios sin coordenadas.
                int columns = (int) Math.ceil(Math.sqrt(state.provinces().size()));
                int col = missing % columns;
                int row = missing / columns;
                int x = 40 + col * 150;
                int y = 40 + row * 100;
                polygon.addPoint(x, y);
                polygon.addPoint(x + 130, y);
                polygon.addPoint(x + 130, y + 80);
                polygon.addPoint(x, y + 80);
                missing++;
            }
            polygons.put(province.id(), polygon);
        }
    }

    // --------------------------------------------------------------- selección

    /** Selecciona una provincia y resalta sus destinos alcanzables. */
    public void select(String provinceId) {
        selectedId = provinceId;
        highlightedIds.clear();
        if (provinceId != null) {
            for (Province target : state.reachableFrom(provinceId)) {
                highlightedIds.add(target.id());
            }
        }
        repaint();
    }

    public String selectedId() {
        return selectedId;
    }

    public boolean isHighlighted(String provinceId) {
        return highlightedIds.contains(provinceId);
    }

    // ------------------------------------------------------------------ dibujo

    private AffineTransform mapTransform() {
        double scale = Math.min(getWidth() / (double) mapWidth, getHeight() / (double) mapHeight);
        AffineTransform transform = new AffineTransform();
        transform.translate((getWidth() - mapWidth * scale) / 2, (getHeight() - mapHeight * scale) / 2);
        transform.scale(scale, scale);
        return transform;
    }

    private Province provinceAt(Point point) {
        try {
            Point2D mapPoint = mapTransform().inverseTransform(point, null);
            for (Map.Entry<String, Polygon> entry : polygons.entrySet()) {
                if (entry.getValue().contains(mapPoint)) {
                    return state.province(entry.getKey());
                }
            }
        } catch (NoninvertibleTransformException ignored) {
            // escala 0: panel aún sin tamaño
        }
        return null;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        Province province = provinceAt(event.getPoint());
        if (province == null) {
            return null;
        }
        if (province.isWater()) {
            return province.name() + " (zona marítima)";
        }
        String owner = province.isNeutral() ? "neutral" : state.nation(province.ownerId()).name();
        return String.format("<html><b>%s</b> — %s<br>poblacion: %,d · descontento: %.0f%% · tropas: %d · fort: %d</html>",
                province.name(), owner, province.population(), province.discontent(),
                province.troops(), province.fortification());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(OCEAN);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.transform(mapTransform());

        for (Province province : state.provinces()) {
            Polygon polygon = polygons.get(province.id());
            g2.setColor(fillColor(province));
            g2.fillPolygon(polygon);
            if (province.id().equals(selectedId)) {
                g2.setColor(Color.YELLOW);
                g2.setStroke(new BasicStroke(4f));
            } else if (highlightedIds.contains(province.id())) {
                g2.setColor(new Color(255, 180, 60));
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{8f, 6f}, 0f));
            } else {
                g2.setColor(new Color(0, 0, 0, 120));
                g2.setStroke(new BasicStroke(1.5f));
            }
            g2.drawPolygon(polygon);
            drawLabel(g2, province, polygon);
        }
        g2.dispose();
    }

    private Color fillColor(Province province) {
        if (province.isWater()) {
            return SEA;
        }
        if (province.isNeutral()) {
            return NEUTRAL;
        }
        return nationColors.getOrDefault(province.ownerId(), NEUTRAL);
    }

    private void drawLabel(Graphics2D g2, Province province, Polygon polygon) {
        Rectangle bounds = polygon.getBounds();
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;

        g2.setColor(province.isWater() ? new Color(255, 255, 255, 150) : Color.WHITE);
        g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        drawCentered(g2, province.name(), centerX, centerY - 6);

        if (!province.isWater()) {
            StringBuilder info = new StringBuilder();
            if (province.troops() > 0 || !province.isNeutral()) {
                info.append(province.troops());
            }
            if (!province.isNeutral()
                    && province.id().equals(state.nation(province.ownerId()).kingProvinceId())) {
                info.append(" K");
            }
            if (province.fortification() > 0) {
                info.append(" F").append(province.fortification());
            }
            g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
            drawCentered(g2, info.toString(), centerX, centerY + 12);
        }
    }

    private void drawCentered(Graphics2D g2, String text, int x, int y) {
        if (text.isEmpty()) {
            return;
        }
        FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(text, x - metrics.stringWidth(text) / 2, y + metrics.getAscent() / 2);
    }

    /** Color asignado a una nación en el mapa (para la interfaz). */
    public Color colorOf(String nationId) {
        return nationColors.getOrDefault(nationId, NEUTRAL);
    }
}
