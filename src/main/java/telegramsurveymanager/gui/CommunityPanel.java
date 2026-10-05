package telegramsurveymanager.gui;

import telegramsurveymanager.model.CommunityMember;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Always-visible global community roster - never replaced by survey-specific views. */
public class CommunityPanel extends JPanel {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final MembersTableModel tableModel = new MembersTableModel();
    private final JLabel countLabel = new JLabel();

    public CommunityPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("👥 חברי הקהילה");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title, BorderLayout.NORTH);

        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);

        countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD));
        add(countLabel, BorderLayout.SOUTH);

        refresh(List.of());
    }

    public void refresh(List<CommunityMember> members) {
        tableModel.setMembers(members);
        countLabel.setText("סה\"כ חברים בקהילה: " + members.size());
    }

    private static class MembersTableModel extends AbstractTableModel {
        private final String[] columns = {"שם", "שם משתמש בטלגרם", "מועד הצטרפות"};
        private List<CommunityMember> members = new ArrayList<>();

        void setMembers(List<CommunityMember> members) {
            this.members = members;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return members.size();
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
            CommunityMember member = members.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> member.displayName();
                case 1 -> member.telegramUsername() != null ? "@" + member.telegramUsername() : "-";
                case 2 -> member.joinedAt().format(TIME_FORMAT);
                default -> "";
            };
        }
    }
}
