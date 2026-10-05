package telegramsurveymanager.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {

    private final Properties properties = new Properties();

    public AppConfig() {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                        "src/main/resources/config.properties לא נמצא. העתק את config.example.properties " +
                                "לנתיב הזה ומלא טוקנים אמיתיים.");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("שגיאה בטעינת config.properties", e);
        }
        requireNonBlank("telegram.bot.token");
        requireNonBlank("telegram.bot.username");
        requireNonBlank("ai.proxy.url");
        requireNonBlank("ai.proxy.token");
    }

    private void requireNonBlank(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("השדה " + key + " חסר בקובץ config.properties.");
        }
    }

    public String getBotToken() {
        return properties.getProperty("telegram.bot.token");
    }

    public String getBotUsername() {
        return properties.getProperty("telegram.bot.username");
    }

    public String getAiProxyUrl() {
        return properties.getProperty("ai.proxy.url");
    }

    public String getAiProxyToken() {
        return properties.getProperty("ai.proxy.token");
    }
}
