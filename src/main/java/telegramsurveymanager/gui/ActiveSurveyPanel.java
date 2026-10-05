package telegramsurveymanager.gui;

import telegramsurveymanager.model.Survey;
import telegramsurveymanager.model.SurveyParticipant;
import telegramsurveymanager.model.SurveyStatus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Live view of the current survey: a countdown while it is scheduled, then a
 * live per-participant progress table with running stats while it is active.
 */
public class ActiveSurveyPanel extends JPanel {

    private static final Color DONE_COLOR = new Color(0x1B5E20);
    private static final Color IN_PROGRESS_COLOR = new Color(0xE65100);
    private static final Color PENDING_COLOR = new Color(0x757575);

    private final JLabel statusBanner = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel countdownLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel statsLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JProgressBar overallProgressBar = new JProgressBar(0, 100);
    private final ParticipantsTableModel tableModel = new ParticipantsTableModel();

    private final Timer timer;
    private Survey survey;

    public ActiveSurveyPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        statusBanner.setFont(statusBanner.getFont().deriveFont(Font.BOLD, 16f));
        statusBanner.setAlignmentX(Component.CENTER_ALIGNMENT);
        countdownLabel.setFont(countdownLabel.getFont().deriveFont(Font.BOLD, 28f));
        countdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statsLabel.setFont(statsLabel.getFont().deriveFont(14f));
        statsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        overallProgressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        overallProgressBar.setStringPainted(true);
        overallProgressBar.setMaximumSize(new Dimension(500, 22));
        overallProgressBar.setPreferredSize(new Dimension(500, 22));
        top.add(statusBanner);
        top.add(countdownLabel);
        top.add(statsLabel);
        top.add(Box.createVerticalStrut(6));
        top.add(overallProgressBar);
        add(top, BorderLayout.NORTH);

        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(2).setCellRenderer(new StatusCellRenderer());
        add(new JScrollPane(table), BorderLayout.CENTER);

        timer = new Timer(1000, e -> tick());
    }

    public void showScheduled(Survey survey) {
        this.survey = survey;
        tableModel.setParticipants(List.of());
        statusBanner.setText("⏳ הסקר נוצר ומתוזמן להישלח בעוד:");
        statsLabel.setText(" ");
        overallProgressBar.setValue(0);
        overallProgressBar.setString(" ");
        timer.start();
        tick();
    }

    public void showActive(Survey survey) {
        this.survey = survey;
        tableModel.setParticipants(new ArrayList<>(survey.getParticipants()));
        statusBanner.setText("✅ הסקר נשלח בהצלחה ל-" + survey.getParticipantCount() + " משתתפים!");
        timer.start();
        tick();
    }

    public void updateProgress(Survey survey) {
        this.survey = survey;
        tableModel.setParticipants(new ArrayList<>(survey.getParticipants()));
        tick();
    }

    public void stop() {
        timer.stop();
    }

    private void tick() {
        if (survey == null) {
            return;
        }
        if (survey.getStatus() == SurveyStatus.SCHEDULED) {
            Duration remaining = Duration.between(LocalDateTime.now(), survey.getScheduledStartAt());
            countdownLabel.setText(formatDuration(remaining));
        } else if (survey.getStatus() == SurveyStatus.ACTIVE) {
            Duration remaining = Duration.between(LocalDateTime.now(), survey.getClosesAt());
            countdownLabel.setText("זמן שנותר: " + formatDuration(remaining));
            int completed = survey.getCompletedCount();
            int total = survey.getParticipantCount();
            statsLabel.setText(String.format("משתתפים: %d  |  השלימו: %d  |  טרם השלימו: %d",
                    total, completed, total - completed));
            int percent = total == 0 ? 0 : Math.round(completed * 100f / total);
            overallProgressBar.setValue(percent);
            overallProgressBar.setString(completed + "/" + total + " השלימו (" + percent + "%)");
            tableModel.fireUpdate();
        }
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = Math.max(0, duration.getSeconds());
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static String statusText(SurveyParticipant participant) {
        if (participant.isCompleted()) {
            return "✅ השלים";
        }
        if (participant.getAnsweredCount() > 0) {
            return "🔄 בתהליך";
        }
        return "⏳ טרם ענה";
    }

    private static Color statusColor(SurveyParticipant participant) {
        if (participant.isCompleted()) {
            return DONE_COLOR;
        }
        if (participant.getAnsweredCount() > 0) {
            return IN_PROGRESS_COLOR;
        }
        return PENDING_COLOR;
    }

    /** Colors the "מצב" column so progress reads at a glance, not just from the emoji/text. */
    private class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            SurveyParticipant participant = tableModel.participantAt(row);
            if (participant != null && !isSelected) {
                c.setForeground(statusColor(participant));
            }
            setFont(getFont().deriveFont(Font.BOLD));
            return c;
        }
    }

    private static class ParticipantsTableModel extends AbstractTableModel {
        private final String[] columns = {"שם", "התקדמות", "מצב"};
        private List<SurveyParticipant> participants = new ArrayList<>();

        void setParticipants(List<SurveyParticipant> participants) {
            this.participants = participants;
            fireTableDataChanged();
        }

        void fireUpdate() {
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return participants.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SurveyParticipant participant = participants.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> participant.getMember().displayName();
                case 1 -> participant.getAnsweredCount() + "/" + participant.getQuestionCount();
                case 2 -> statusText(participant);
                default -> "";
            };
        }

        SurveyParticipant participantAt(int rowIndex) {
            return rowIndex >= 0 && rowIndex < participants.size() ? participants.get(rowIndex) : null;
        }
    }
}
