package telegramsurveymanager;

import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import telegramsurveymanager.bot.SurveyBot;
import telegramsurveymanager.config.AppConfig;
import telegramsurveymanager.gui.DashboardGUI;
import telegramsurveymanager.service.CommunityService;
import telegramsurveymanager.service.SurveyService;
import telegramsurveymanager.service.ai.ProxyAiSurveyGenerator;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        AppConfig config = new AppConfig();

        CommunityService communityService = new CommunityService();
        SurveyService surveyService = new SurveyService(communityService);

        SurveyBot bot = new SurveyBot(config.getBotToken(), communityService, surveyService);
        communityService.addListener(bot);
        surveyService.addListener(bot);

        try {
            TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(config.getBotToken(), bot);
            System.out.println("הבוט @" + config.getBotUsername() + " עלה בהצלחה ומאזין לעדכונים.");
        } catch (Exception e) {
            System.err.println("כשל בחיבור לבוט הטלגרם - בדוק את telegram.bot.token ב-config.properties.");
            e.printStackTrace();
            return;
        }

        ProxyAiSurveyGenerator aiSurveyGenerator =
                new ProxyAiSurveyGenerator(config.getAiProxyUrl(), config.getAiProxyToken());

        SwingUtilities.invokeLater(() -> {
            DashboardGUI gui = new DashboardGUI(communityService, surveyService, aiSurveyGenerator);
            gui.setVisible(true);
        });
    }
}
