import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class OpenAIService {

    private static final String TOKEN = "3rugqeuljJtZj31MShOhJ6J7lnRPu0i28skmPnu5kfxtDC3I0AtFaIv2Ek8f3blu";
    private static final String BASE_URL = "https://shaitest-production-3066.up.railway.app/api-request";

    public static String[] generateSurveyFromTopic(String topic) {
        try {
            String prompt = "צור שאלה אחת לסקר בנושא: " + topic + ". החזר אך ורק בפורמט הבא בדיוק מבלי להוסיף שום מילה מסביב: שאלה: [השאלה] | תשובות: [תשובה1], [תשובה2], [תשובה3], [תשובה4]";
            String encodedTopic = URLEncoder.encode(prompt, StandardCharsets.UTF_8.toString());

            String fullUrlStr = BASE_URL + "?token=" + TOKEN + "&text=" + encodedTopic;

            URL url = new URL(fullUrlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                String rawResponse = response.toString().trim();
                String cleanedText = cleanJsonResponse(rawResponse);

                return parseGptResponse(cleanedText, topic);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new String[]{
                "מה דעתך על הנושא: " + topic + "?",
                "מעולה", "טוב מאוד", "ניטרלי", "טעון שיפור"
        };
    }

    private static String cleanJsonResponse(String response) {
        try {
            int questionIndex = response.indexOf("שאלה:");
            if (questionIndex != -1) {
                return response.substring(questionIndex).replace("\\\"", "\"").replace("\\n", " ");
            }

            response = response.replace("{", "").replace("}", "").replace("\"", "");
            if (response.contains("text:")) {
                int idx = response.indexOf("text:");
                response = response.substring(idx + 5);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
    }

    private static String[] parseGptResponse(String content, String fallbackTopic) {
        try {
            if (content.contains("שאלה:") && content.contains("תשובות:")) {
                String[] parts = content.split("\\|");
                String qText = parts[0].replace("שאלה:", "").trim();
                String ansPart = parts[1].replace("תשובות:", "").replace("]", "").replace("[", "").trim();
                String[] options = ansPart.split(",");

                if (options.length >= 2) {
                    String[] result = new String[Math.min(options.length + 1, 5)];
                    result[0] = qText;
                    for (int i = 0; i < options.length && i < 4; i++) {
                        result[i + 1] = options[i].trim();
                    }
                    return result;
                }
            }

            if (!content.isEmpty()) {
                return new String[]{
                        content.replaceAll("[{}\"\\\\]", "").trim(),
                        "מסכים בהחלט", "נוטה להסכים", "מתנגד", "אדיש"
                };
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new String[]{
                "שאלה אוטומטית בנושא: " + fallbackTopic,
                "כן", "לא", "אולי", "לא ידוע"
        };
    }
}