package telegramsurveymanager.gui;

import telegramsurveymanager.model.QuestionResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

/** Final results view: options sorted most-voted first, with percentage bars. */
public class ResultsPanel extends JPanel {

    private static final Color WINNER_COLOR = new Color(0x2E7D32);
    private static final String[] RANK_MARKERS = {"🥇", "🥈", "🥉"};

    private final JPanel resultsContainer = new JPanel();

    public ResultsPanel(Runnable onStartNewSurvey) {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("🏁 תוצאות הסקר");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        resultsContainer.setLayout(new BoxLayout(resultsContainer, BoxLayout.Y_AXIS));
        add(new JScrollPane(resultsContainer), BorderLayout.CENTER);

        JButton newSurveyButton = new JButton("🔄 התחל סקר חדש");
        newSurveyButton.setFont(newSurveyButton.getFont().deriveFont(Font.BOLD, 13f));
        newSurveyButton.addActionListener(e -> onStartNewSurvey.run());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEADING));
        footer.add(newSurveyButton);
        add(footer, BorderLayout.SOUTH);
    }

    public void showResults(List<QuestionResult> results) {
        resultsContainer.removeAll();
        int questionNumber = 1;
        for (QuestionResult result : results) {
            JPanel card = buildQuestionCard(questionNumber++, result);
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            resultsContainer.add(card);
            resultsContainer.add(Box.createVerticalStrut(10));
        }
        resultsContainer.revalidate();
        resultsContainer.repaint();
    }

    private JPanel buildQuestionCard(int number, QuestionResult result) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new TitledBorder("שאלה " + number + ": " + result.questionText()));

        List<QuestionResult.OptionResult> options = result.options();
        for (int rank = 0; rank < options.size(); rank++) {
            JPanel row = buildOptionRow(rank, options.get(rank));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(row);
        }
        return card;
    }

    private JPanel buildOptionRow(int rank, QuestionResult.OptionResult option) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(new EmptyBorder(4, 4, 4, 4));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        String marker = rank < RANK_MARKERS.length ? RANK_MARKERS[rank] + " " : (rank + 1) + ". ";
        JLabel label = new JLabel(marker + option.text());
        if (rank == 0 && option.votes() > 0) {
            label.setFont(label.getFont().deriveFont(Font.BOLD));
        }
        label.setPreferredSize(new Dimension(190, 20));
        row.add(label, BorderLayout.LINE_START);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue((int) Math.round(option.percentage()));
        bar.setStringPainted(true);
        bar.setString(String.format("%.0f%% (%d הצבעות)", option.percentage(), option.votes()));
        if (rank == 0 && option.votes() > 0) {
            bar.setForeground(WINNER_COLOR);
        }
        row.add(bar, BorderLayout.CENTER);

        return row;
    }
}
