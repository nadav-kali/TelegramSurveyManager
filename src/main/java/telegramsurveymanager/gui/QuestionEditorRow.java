package telegramsurveymanager.gui;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** One editable question (text + 2-4 option fields) inside {@link SurveyCreationPanel}. */
class QuestionEditorRow extends JPanel {

    private static final int MAX_OPTIONS = 4;
    private static final int MIN_OPTIONS = 2;

    private final JLabel titleLabel;
    private final JTextField questionField = new JTextField();
    private final JPanel optionsPanel = new JPanel();
    private final List<JTextField> optionFields = new ArrayList<>();
    private final JButton addOptionButton = new JButton("+ אפשרות");
    private final JButton removeOptionButton = new JButton("- אפשרות");

    QuestionEditorRow(int number, Consumer<QuestionEditorRow> onRemove) {
        setLayout(new BorderLayout(6, 4));
        setBorder(new CompoundBorder(new LineBorder(new Color(0xCCCCCC)), new EmptyBorder(8, 8, 8, 8)));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));

        JPanel top = new JPanel(new BorderLayout(6, 0));
        titleLabel = new JLabel("שאלה " + number + ":");
        top.add(titleLabel, BorderLayout.LINE_START);
        top.add(questionField, BorderLayout.CENTER);

        JButton removeRowButton = new JButton("✕");
        removeRowButton.setToolTipText("הסר שאלה זו");
        removeRowButton.addActionListener(e -> onRemove.accept(this));
        top.add(removeRowButton, BorderLayout.LINE_END);

        add(top, BorderLayout.NORTH);

        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        addOptionField();
        addOptionField();
        add(optionsPanel, BorderLayout.CENTER);

        JPanel optionButtons = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        addOptionButton.addActionListener(e -> addOptionField());
        removeOptionButton.addActionListener(e -> removeOptionField());
        optionButtons.add(addOptionButton);
        optionButtons.add(removeOptionButton);
        add(optionButtons, BorderLayout.SOUTH);

        updateOptionButtons();
    }

    void setNumber(int number) {
        titleLabel.setText("שאלה " + number + ":");
    }

    private void addOptionField() {
        if (optionFields.size() >= MAX_OPTIONS) {
            return;
        }
        JPanel row = new JPanel(new BorderLayout(4, 0));
        JLabel label = new JLabel("אפשרות " + (optionFields.size() + 1) + ":");
        label.setPreferredSize(new Dimension(80, 20));
        JTextField field = new JTextField();
        optionFields.add(field);
        row.add(label, BorderLayout.LINE_START);
        row.add(field, BorderLayout.CENTER);
        optionsPanel.add(row);
        optionsPanel.revalidate();
        optionsPanel.repaint();
        updateOptionButtons();
    }

    private void removeOptionField() {
        if (optionFields.size() <= MIN_OPTIONS) {
            return;
        }
        optionFields.remove(optionFields.size() - 1);
        optionsPanel.remove(optionsPanel.getComponentCount() - 1);
        optionsPanel.revalidate();
        optionsPanel.repaint();
        updateOptionButtons();
    }

    private void updateOptionButtons() {
        addOptionButton.setEnabled(optionFields.size() < MAX_OPTIONS);
        removeOptionButton.setEnabled(optionFields.size() > MIN_OPTIONS);
    }

    String getQuestionText() {
        return questionField.getText().trim();
    }

    void setQuestionText(String text) {
        questionField.setText(text);
    }

    List<String> getOptions() {
        List<String> options = new ArrayList<>();
        for (JTextField field : optionFields) {
            options.add(field.getText().trim());
        }
        return options;
    }

    void setOptions(List<String> options) {
        while (optionFields.size() > options.size() && optionFields.size() > MIN_OPTIONS) {
            removeOptionField();
        }
        while (optionFields.size() < options.size() && optionFields.size() < MAX_OPTIONS) {
            addOptionField();
        }
        for (int i = 0; i < optionFields.size() && i < options.size(); i++) {
            optionFields.get(i).setText(options.get(i));
        }
    }
}
