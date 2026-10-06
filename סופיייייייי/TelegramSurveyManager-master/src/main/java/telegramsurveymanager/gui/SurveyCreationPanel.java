package telegramsurveymanager.gui;

import telegramsurveymanager.model.Question;
import telegramsurveymanager.service.SurveyService;
import telegramsurveymanager.service.ai.AiGenerationException;
import telegramsurveymanager.service.ai.AiSurveyGenerator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Survey authoring screen: manual question editing, optional AI-assisted
 * generation (which just pre-fills the same editable fields), and the
 * immediate/delayed send choice.
 */
public class SurveyCreationPanel extends JPanel {

    private static final int MAX_QUESTIONS = 3;
    private static final int MIN_QUESTIONS = 1;
    private static final int MIN_OPTIONS = 2;

    private static final Color ERROR_COLOR = new Color(0xB00020);
    private static final Color OK_COLOR = new Color(0x1B5E20);
    private static final Color MUTED_COLOR = new Color(0x555555);

    private final SurveyService surveyService;
    private final AiSurveyGenerator aiSurveyGenerator;

    private final JPanel questionsContainer = new JPanel();
    private final List<QuestionEditorRow> rows = new ArrayList<>();
    private final JButton addQuestionButton = new JButton("+ הוסף שאלה");

    private final JTextField topicField = new JTextField();
    private final JSpinner aiQuestionCountSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 3, 1));
    private final JSpinner aiOptionCountSpinner = new JSpinner(new SpinnerNumberModel(4, 2, 4, 1));
    private final JButton generateWithAiButton = new JButton("✨ צור שאלות עם AI");
    private final JLabel aiStatusLabel = new JLabel(" ");

    private final JRadioButton immediateRadio = new JRadioButton("מיידי", true);
    private final JRadioButton delayedRadio = new JRadioButton("מושהה בדקות:");
    private final JSpinner delayMinutesSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 60, 1));

    private final JLabel communityStatusLabel = new JLabel(" ");
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton startButton = new JButton("🚀 התחל סקר");

    public SurveyCreationPanel(SurveyService surveyService, AiSurveyGenerator aiSurveyGenerator) {
        this.surveyService = surveyService;
        this.aiSurveyGenerator = aiSurveyGenerator;

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(15, 15, 15, 15));

        add(buildAiHeader(), BorderLayout.NORTH);

        questionsContainer.setLayout(new BoxLayout(questionsContainer, BoxLayout.Y_AXIS));
        addRow();
        addRow();

        JPanel questionsWrapper = new JPanel(new BorderLayout());
        questionsWrapper.setBorder(new TitledBorder("שאלות הסקר (1-3 שאלות, 2-4 אפשרויות לכל שאלה)"));
        questionsWrapper.add(new JScrollPane(questionsContainer), BorderLayout.CENTER);

        JPanel addRowWrapper = new JPanel(new FlowLayout(FlowLayout.LEADING));
        addQuestionButton.addActionListener(e -> addRow());
        addRowWrapper.add(addQuestionButton);
        questionsWrapper.add(addRowWrapper, BorderLayout.SOUTH);

        add(questionsWrapper, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        updateAddButtonState();
        refreshState();
    }

    private JPanel buildAiHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new TitledBorder("יצירה אוטומטית עם AI (אופציונלי)"));

        // Two short rows instead of one long one - stays readable even when the
        // panel is narrow, instead of silently wrapping/clipping the generate button.
        JPanel topicRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 4));
        topicRow.add(new JLabel("נושא:"));
        topicField.setColumns(20);
        topicRow.add(topicField);

        JPanel optionsRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 4));
        optionsRow.add(new JLabel("מס' שאלות:"));
        optionsRow.add(aiQuestionCountSpinner);
        optionsRow.add(new JLabel("אפשרויות לשאלה:"));
        optionsRow.add(aiOptionCountSpinner);
        generateWithAiButton.addActionListener(e -> generateWithAi());
        optionsRow.add(generateWithAiButton);

        aiStatusLabel.setForeground(MUTED_COLOR);
        header.add(topicRow);
        header.add(optionsRow);
        header.add(aiStatusLabel);
        return header;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));

        JPanel timing = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 4));
        timing.setBorder(new TitledBorder("מועד שליחה"));
        ButtonGroup group = new ButtonGroup();
        group.add(immediateRadio);
        group.add(delayedRadio);
        timing.add(immediateRadio);
        timing.add(delayedRadio);
        timing.add(delayMinutesSpinner);
        footer.add(timing);

        communityStatusLabel.setForeground(MUTED_COLOR);
        footer.add(communityStatusLabel);

        errorLabel.setForeground(ERROR_COLOR);
        footer.add(errorLabel);

        JPanel startWrapper = new JPanel(new FlowLayout(FlowLayout.LEADING));
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD, 14f));
        startButton.addActionListener(e -> onStartClicked());
        startWrapper.add(startButton);
        footer.add(startWrapper);

        return footer;
    }

    private void addRow() {
        if (rows.size() >= MAX_QUESTIONS) {
            return;
        }
        QuestionEditorRow row = new QuestionEditorRow(rows.size() + 1, this::removeRow);
        rows.add(row);
        questionsContainer.add(row);
        renumberRows();
        questionsContainer.revalidate();
        questionsContainer.repaint();
        updateAddButtonState();
    }

    private void removeRow(QuestionEditorRow row) {
        if (rows.size() <= MIN_QUESTIONS) {
            return;
        }
        rows.remove(row);
        questionsContainer.remove(row);
        renumberRows();
        questionsContainer.revalidate();
        questionsContainer.repaint();
        updateAddButtonState();
    }

    private void removeRowSilently(QuestionEditorRow row) {
        rows.remove(row);
        questionsContainer.remove(row);
    }

    private void renumberRows() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setNumber(i + 1);
        }
    }

    private void updateAddButtonState() {
        addQuestionButton.setEnabled(rows.size() < MAX_QUESTIONS);
    }

    private void generateWithAi() {
        String topic = topicField.getText().trim();
        if (topic.isEmpty()) {
            aiStatusLabel.setForeground(ERROR_COLOR);
            aiStatusLabel.setText("נא להזין נושא לפני יצירה עם AI.");
            return;
        }
        int questionCount = (Integer) aiQuestionCountSpinner.getValue();
        int optionCount = (Integer) aiOptionCountSpinner.getValue();

        generateWithAiButton.setEnabled(false);
        aiStatusLabel.setForeground(MUTED_COLOR);
        aiStatusLabel.setText("יוצר שאלות עם AI... נא להמתין.");

        SwingWorker<List<Question>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Question> doInBackground() throws Exception {
                return aiSurveyGenerator.generate(topic, questionCount, optionCount);
            }

            @Override
            protected void done() {
                generateWithAiButton.setEnabled(true);
                try {
                    applyGeneratedQuestions(get());
                    aiStatusLabel.setForeground(OK_COLOR);
                    aiStatusLabel.setText("השאלות נוצרו בהצלחה! אפשר לערוך אותן למטה לפני שליחה.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() instanceof AiGenerationException ? ex.getCause() : ex;
                    aiStatusLabel.setForeground(ERROR_COLOR);
                    aiStatusLabel.setText("יצירת השאלות נכשלה: " + cause.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void applyGeneratedQuestions(List<Question> generated) {
        for (QuestionEditorRow row : new ArrayList<>(rows)) {
            removeRowSilently(row);
        }
        for (Question question : generated) {
            addRow();
            QuestionEditorRow row = rows.get(rows.size() - 1);
            row.setQuestionText(question.text());
            row.setOptions(question.options());
        }
        questionsContainer.revalidate();
        questionsContainer.repaint();
    }

    public void refreshState() {
        String reason = surveyService.canStartSurvey();
        startButton.setEnabled(reason == null);
        if (reason == null) {
            communityStatusLabel.setForeground(OK_COLOR);
            communityStatusLabel.setText("ניתן להתחיל סקר.");
        } else {
            communityStatusLabel.setForeground(ERROR_COLOR);
            communityStatusLabel.setText(reason);
        }
    }

    public void resetForNewSurvey() {
        for (QuestionEditorRow row : new ArrayList<>(rows)) {
            removeRowSilently(row);
        }
        addRow();
        addRow();
        topicField.setText("");
        aiStatusLabel.setText(" ");
        errorLabel.setText(" ");
        immediateRadio.setSelected(true);
        delayMinutesSpinner.setValue(5);
        refreshState();
    }

    private void onStartClicked() {
        errorLabel.setText(" ");
        String reason = surveyService.canStartSurvey();
        if (reason != null) {
            errorLabel.setText(reason);
            return;
        }

        List<Question> questions;
        try {
            questions = collectQuestions();
        } catch (IllegalArgumentException ex) {
            errorLabel.setText(ex.getMessage());
            return;
        }

        int delayMinutes = immediateRadio.isSelected() ? 0 : (Integer) delayMinutesSpinner.getValue();

        // Immediate feedback the instant the user clicks - the actual card switch only
        // happens once onSurveyScheduled/onSurveyStarted fires from the scheduler thread,
        // which is normally near-instant but should never leave the button looking inert.
        startButton.setEnabled(false);
        communityStatusLabel.setForeground(OK_COLOR);
        communityStatusLabel.setText("🚀 שולח...");

        try {
            surveyService.createSurvey(questions, delayMinutes);
        } catch (RuntimeException ex) {
            errorLabel.setText(ex.getMessage());
            refreshState();
            return;
        }
    }

    private List<Question> collectQuestions() {
        List<Question> questions = new ArrayList<>();
        for (QuestionEditorRow row : rows) {
            String text = row.getQuestionText();
            if (text.isEmpty()) {
                throw new IllegalArgumentException("יש למלא את נוסח כל שאלה.");
            }
            List<String> options = row.getOptions();
            if (options.size() < MIN_OPTIONS) {
                throw new IllegalArgumentException("לכל שאלה נדרשות לפחות " + MIN_OPTIONS + " אפשרויות תשובה.");
            }
            for (String option : options) {
                if (option.isEmpty()) {
                    throw new IllegalArgumentException("יש למלא את כל אפשרויות התשובה (או להסיר שדות ריקים).");
                }
            }
            questions.add(new Question(text, options));
        }
        return questions;
    }
}
