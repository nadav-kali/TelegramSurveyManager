package telegramsurveymanager.service.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import telegramsurveymanager.model.Question;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls the course's ChatGPT proxy (not OpenAI directly) and asks it to return
 * one strict JSON object, so parsing does not rely on fragile text splitting.
 */
public class ProxyAiSurveyGenerator implements AiSurveyGenerator {

    private final String baseUrl;
    private final String token;

    public ProxyAiSurveyGenerator(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    @Override
    public List<Question> generate(String topic, int questionCount, int optionsPerQuestion) throws AiGenerationException {
        String prompt = buildPrompt(topic, questionCount, optionsPerQuestion);
        String rawValue = callProxy(prompt);
        return parseQuestions(rawValue);
    }

    private String buildPrompt(String topic, int questionCount, int optionsPerQuestion) {
        return "צור סקר בעברית בנושא: \"" + topic + "\". " +
                "בדיוק " + questionCount + " שאלות, ולכל שאלה בדיוק " + optionsPerQuestion +
                " אפשרויות תשובה קצרות וברורות. " +
                "החזר אך ורק JSON תקין, ללא כל טקסט נוסף וללא markdown, במבנה המדויק הבא: " +
                "{\"questions\":[{\"text\":\"...\",\"options\":[\"...\"]}]}";
    }

    private String callProxy(String prompt) throws AiGenerationException {
        try {
            String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
            String fullUrl = baseUrl + "?token=" + token + "&text=" + encodedPrompt;

            HttpURLConnection connection = (HttpURLConnection) URI.create(fullUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new AiGenerationException("שרת ה-AI החזיר קוד שגיאה: " + responseCode);
            }

            String body;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                body = sb.toString();
            }

            JSONObject envelope = new JSONObject(body);
            if (envelope.optBoolean("error", false)) {
                throw new AiGenerationException("שרת ה-AI דיווח על שגיאה: " + envelope.optString("code", "לא ידוע"));
            }
            return envelope.getString("value");
        } catch (AiGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new AiGenerationException("תקלה בתקשורת עם שרת ה-AI: " + e.getMessage(), e);
        }
    }

    // package-private (not private) so unit tests can exercise parsing without a network call
    List<Question> parseQuestions(String rawValue) throws AiGenerationException {
        try {
            JSONObject root = new JSONObject(extractJsonObject(rawValue));
            JSONArray questionsArray = root.getJSONArray("questions");

            List<Question> questions = new ArrayList<>();
            for (int i = 0; i < questionsArray.length(); i++) {
                JSONObject questionObj = questionsArray.getJSONObject(i);
                String text = questionObj.getString("text").trim();
                JSONArray optionsArray = questionObj.getJSONArray("options");

                List<String> options = new ArrayList<>();
                for (int j = 0; j < optionsArray.length(); j++) {
                    options.add(optionsArray.getString(j).trim());
                }
                questions.add(new Question(text, options));
            }

            if (questions.isEmpty() || questions.size() > 3) {
                throw new AiGenerationException("ה-AI החזיר " + questions.size() + " שאלות, אך נדרשות 1-3.");
            }
            for (Question question : questions) {
                int optionCount = question.options().size();
                if (optionCount < 2 || optionCount > 4) {
                    throw new AiGenerationException(
                            "השאלה \"" + question.text() + "\" חזרה עם " + optionCount + " אפשרויות, אך נדרשות 2-4.");
                }
            }
            return questions;
        } catch (AiGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new AiGenerationException("לא ניתן לפענח את תגובת ה-AI כ-JSON תקין.", e);
        }
    }

    private String extractJsonObject(String text) throws AiGenerationException {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            throw new AiGenerationException("תגובת ה-AI לא הכילה JSON תקין.");
        }
        return text.substring(start, end + 1);
    }
}
