package ui;

import ai.Agent;
import ai.GreedyAgent;
import engine.Order;
import engine.OrderException;
import engine.TurnEngine;
import engine.TurnReport;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import model.DiplomaticState;
import model.GameState;
import model.Nation;
import model.Province;

/**
 * Interfaz gráfica de la partida (fase M6): mapa clicable a la izquierda,
 * panel de nación y acciones a la derecha, crónica de la partida abajo.
 * Las naciones humanas planifican en hotseat; las IA juegan solas al pulsar
 * "Fin de turno". Sin naciones humanas, la partida corre sola (espectador).
 */
public class SwingGame {

    private final GameState state;
    private final TurnEngine engine;
    private final Agent aiAgent = new GreedyAgent();

    private final Deque<String> pendingHumans = new ArrayDeque<>();
    private Nation currentHuman;

    private JFrame frame;
    private MapPanel mapPanel;
    private JLabel turnLabel;
    private JLabel nationLabel;
    private JLabel statsLabel;
    private JLabel provinceLabel;
    private JTextArea logArea;
    private JButton endTurnButton;
    private JPanel actionsPanel;

    public SwingGame(GameState state) {
        this.state = state;
        this.engine = new TurnEngine(state);
    }

    public void start() {
        frame = new JFrame("Age of Conquest — Clon · " + state.scenarioName());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        mapPanel = new MapPanel(state, this::onProvinceClicked);
        mapPanel.setPreferredSize(new Dimension(760, 620));
        frame.add(mapPanel, BorderLayout.CENTER);
        frame.add(buildSidePanel(), BorderLayout.EAST);
        frame.add(buildLogPanel(), BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        log("=== " + state.scenarioName() + " ===");
        beginTurn();
    }

    // ------------------------------------------------------------ construcción

    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(10, 6, 10, 10));
        side.setPreferredSize(new Dimension(280, 620));

        turnLabel = new JLabel();
        turnLabel.setFont(turnLabel.getFont().deriveFont(Font.BOLD, 16f));
        nationLabel = new JLabel();
        nationLabel.setFont(nationLabel.getFont().deriveFont(Font.BOLD, 15f));
        nationLabel.setOpaque(true);
        nationLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statsLabel = new JLabel();
        provinceLabel = new JLabel("<html><i>Haz clic en una provincia…</i></html>");
        provinceLabel.setBorder(BorderFactory.createTitledBorder("Provincia"));
        provinceLabel.setPreferredSize(new Dimension(260, 110));

        actionsPanel = new JPanel(new GridLayout(0, 2, 6, 6));
        actionsPanel.setBorder(BorderFactory.createTitledBorder("Órdenes"));
        actionsPanel.add(button("Reclutar", this::recruitDialog));
        actionsPanel.add(button("Fortificar", () -> submitOnSelected(
                id -> new Order.Fortify(currentHuman.id(), id))));
        actionsPanel.add(button("Saquear", () -> submitOnSelected(
                id -> new Order.Pillage(currentHuman.id(), id))));
        actionsPanel.add(button("Repartir oro", () -> submitOnSelected(
                id -> new Order.Decree(currentHuman.id(), id, Order.DecreeType.REPARTIR))));
        actionsPanel.add(button("Fiesta", () -> submitOnSelected(
                id -> new Order.Decree(currentHuman.id(), id, Order.DecreeType.FIESTA))));
        actionsPanel.add(button("Festival", () -> submitOnSelected(
                id -> new Order.Decree(currentHuman.id(), id, Order.DecreeType.FESTIVAL))));
        actionsPanel.add(button("Guerra…", this::declareWarDialog));
        actionsPanel.add(button("Impuestos…", this::taxDialog));

        endTurnButton = new JButton("Fin de turno ▶");
        endTurnButton.setFont(endTurnButton.getFont().deriveFont(Font.BOLD, 15f));
        endTurnButton.setAlignmentX(0f);
        endTurnButton.addActionListener(e -> endTurnPressed());

        for (var component : new java.awt.Component[]{
                turnLabel, nationLabel, statsLabel, provinceLabel, actionsPanel}) {
            if (component instanceof javax.swing.JComponent jc) {
                jc.setAlignmentX(0f);
            }
            side.add(component);
            side.add(Box.createVerticalStrut(8));
        }
        side.add(Box.createVerticalGlue());
        side.add(endTurnButton);
        return side;
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> {
            if (currentHuman != null) {
                action.run();
            }
        });
        return button;
    }

    private JScrollPane buildLogPanel() {
        logArea = new JTextArea(8, 80);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Crónica"));
        return scroll;
    }

    // ------------------------------------------------------------ flujo de turno

    private void beginTurn() {
        pendingHumans.clear();
        for (Nation nation : state.livingNations()) {
            if (!nation.isAI()) {
                pendingHumans.add(nation.id());
            }
        }
        if (pendingHumans.isEmpty()) {
            spectate();
        } else {
            nextHuman();
        }
    }

    private void nextHuman() {
        currentHuman = state.nation(pendingHumans.poll());
        mapPanel.select(null);
        refreshPanels();
    }

    private void endTurnPressed() {
        if (currentHuman == null) {
            return;
        }
        if (!pendingHumans.isEmpty()) {
            nextHuman();
            return;
        }
        currentHuman = null;
        resolveTurn();
        if (!engine.isGameOver()) {
            beginTurn();
        }
    }

    /** Sin humanos vivos: la partida corre sola, un turno cada 300 ms. */
    private void spectate() {
        refreshPanels();
        Timer timer = new Timer(300, null);
        timer.addActionListener(e -> {
            resolveTurn();
            if (engine.isGameOver()) {
                timer.stop();
            }
        });
        timer.start();
    }

    private void resolveTurn() {
        for (Nation nation : state.livingNations()) {
            if (nation.isAI()) {
                aiAgent.plan(engine, nation);
            }
        }
        TurnReport report = engine.endTurn();
        log("");
        log("── Turno " + report.turn() + " ──");
        for (String event : report.events()) {
            log("  " + event);
        }
        mapPanel.select(null);
        refreshPanels();
        if (report.gameOver()) {
            Nation winner = state.nation(report.winnerId());
            JOptionPane.showMessageDialog(frame,
                    "¡" + winner.name() + " domina el mapa!",
                    "Fin de la partida", JOptionPane.INFORMATION_MESSAGE);
            endTurnButton.setEnabled(false);
        }
    }

    // ---------------------------------------------------------------- acciones

    private void onProvinceClicked(Province province) {
        if (province == null) {
            mapPanel.select(null);
            refreshProvincePanel(null);
            return;
        }
        if (currentHuman != null && currentHuman.id().equals(province.ownerId())) {
            mapPanel.select(province.id());
        } else if (currentHuman != null && mapPanel.selectedId() != null
                && mapPanel.isHighlighted(province.id()) && !province.isWater()) {
            moveDialog(state.province(mapPanel.selectedId()), province);
        }
        refreshProvincePanel(province);
    }

    private void moveDialog(Province from, Province to) {
        if (from.troops() < 1) {
            error("No quedan tropas en " + from.name());
            return;
        }
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                from.troops(), 1, from.troops(), 1));
        JCheckBox withKing = new JCheckBox("Llevar al rey");
        withKing.setEnabled(from.id().equals(currentHuman.kingProvinceId()));

        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Tropas de " + from.name() + " → " + to.name() + ":"));
        panel.add(spinner);
        panel.add(withKing);
        int option = JOptionPane.showConfirmDialog(frame, panel, "Mover ejército",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option == JOptionPane.OK_OPTION) {
            submit(new Order.Move(currentHuman.id(), from.id(), to.id(),
                    (Integer) spinner.getValue(), withKing.isSelected()));
        }
    }

    private void recruitDialog() {
        String selected = mapPanel.selectedId();
        if (selected == null) {
            error("Selecciona primero una provincia propia");
            return;
        }
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(100, 1, 1_000_000, 50));
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Soldados a reclutar en " + state.province(selected).name() + ":"));
        panel.add(spinner);
        int option = JOptionPane.showConfirmDialog(frame, panel, "Reclutar",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option == JOptionPane.OK_OPTION) {
            submit(new Order.Recruit(currentHuman.id(), selected, (Integer) spinner.getValue()));
        }
    }

    private void declareWarDialog() {
        JComboBox<String> combo = new JComboBox<>();
        for (Nation nation : state.livingNations()) {
            if (!nation.id().equals(currentHuman.id())
                    && state.relation(currentHuman.id(), nation.id()) == DiplomaticState.NEUTRAL) {
                combo.addItem(nation.id());
            }
        }
        if (combo.getItemCount() == 0) {
            error("No hay naciones neutrales a las que declarar la guerra");
            return;
        }
        int option = JOptionPane.showConfirmDialog(frame, combo, "Declarar la guerra a…",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (option == JOptionPane.OK_OPTION) {
            submit(new Order.DeclareWar(currentHuman.id(), (String) combo.getSelectedItem()));
        }
    }

    private void taxDialog() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                currentHuman.taxRate(), 0, state.rules().thetaMax, 5));
        int option = JOptionPane.showConfirmDialog(frame, spinner, "Tasa impositiva (%)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option == JOptionPane.OK_OPTION) {
            submit(new Order.SetTaxRate(currentHuman.id(), (Integer) spinner.getValue()));
        }
    }

    private void submitOnSelected(java.util.function.Function<String, Order> factory) {
        String selected = mapPanel.selectedId();
        if (selected == null) {
            error("Selecciona primero una provincia propia");
            return;
        }
        submit(factory.apply(selected));
    }

    private void submit(Order order) {
        try {
            engine.submit(order);
            refreshPanels();
        } catch (OrderException e) {
            error(e.getMessage());
        }
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(frame, message, "Orden rechazada", JOptionPane.WARNING_MESSAGE);
    }

    // ------------------------------------------------------------- refrescos

    private void refreshPanels() {
        turnLabel.setText("Turno " + state.turn());
        if (currentHuman == null) {
            nationLabel.setText("Modo espectador (solo IA)");
            nationLabel.setBackground(Color.DARK_GRAY);
            nationLabel.setForeground(Color.WHITE);
            statsLabel.setText(" ");
            for (var component : actionsPanel.getComponents()) {
                component.setEnabled(false);
            }
            endTurnButton.setEnabled(false);
        } else {
            nationLabel.setText("Turno de " + currentHuman.name());
            nationLabel.setBackground(mapPanel.colorOf(currentHuman.id()));
            nationLabel.setForeground(Color.WHITE);
            statsLabel.setText(String.format(
                    "<html>Oro: <b>%.1f</b><br>Impuestos: %d%% · Tropas: %d<br>Rey en: %s</html>",
                    currentHuman.gold(), currentHuman.taxRate(),
                    state.totalTroops(currentHuman.id()),
                    currentHuman.kingProvinceId() == null ? "(muerto)"
                            : state.province(currentHuman.kingProvinceId()).name()));
            for (var component : actionsPanel.getComponents()) {
                component.setEnabled(true);
            }
            endTurnButton.setEnabled(!engine.isGameOver());
        }
        mapPanel.repaint();
    }

    private void refreshProvincePanel(Province province) {
        if (province == null) {
            provinceLabel.setText("<html><i>Haz clic en una provincia…</i></html>");
            return;
        }
        if (province.isWater()) {
            provinceLabel.setText("<html><b>" + province.name() + "</b><br>Zona marítima</html>");
            return;
        }
        String owner = province.isNeutral() ? "neutral" : state.nation(province.ownerId()).name();
        provinceLabel.setText(String.format(
                "<html><b>%s</b> — %s<br>Poblacion: %,d<br>Descontento: %.0f%%<br>Tropas: %d<br>Fort: %d/%d%s</html>",
                province.name(), owner, province.population(), province.discontent(),
                province.troops(), province.fortification(), state.rules().phiMax,
                !province.isNeutral()
                        && province.id().equals(state.nation(province.ownerId()).kingProvinceId())
                        ? " · Rey" : ""));
    }

    private void log(String line) {
        logArea.append(line + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
