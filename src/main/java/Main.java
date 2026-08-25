import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // 1. קודם ניצור את הבוט
            SurveyBot bot = new SurveyBot(null);
            botsApi.registerBot(bot);

            // 2. עכשיו ניצור את החלון הגרפי, ונקשר אותו חזרה לבוט!
            SwingUtilities.invokeLater(() -> {
                DashboardGUI gui = new DashboardGUI(bot);
                bot.setGui(gui); // חיבור קריטי כדי שהבוט יוכל לעדכן את הטבלאות
                gui.setVisible(true);
            });

            System.out.println("הבוט והממשק עלו בהצלחה!");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}