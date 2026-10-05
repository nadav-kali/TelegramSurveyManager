package telegramsurveymanager.bot;

/** Centralized Hebrew copy for everything the bot sends - keeps wording consistent in one place. */
public final class BotMessages {

    private BotMessages() {
    }

    public static String welcome(String displayName) {
        return "ברוך/ה הבא/ה לקהילה, " + displayName + "! 🎉\n" +
                "מעכשיו תקבל/י כאן סקרים של הקהילה.";
    }

    public static String alreadyMember() {
        return "את/ה כבר חבר/ה בקהילה. ברגע שיתחיל סקר חדש תקבל/י עדכון כאן 🙂";
    }

    public static String newMemberBroadcast(String newMemberName, int communitySize) {
        return "👋 " + newMemberName + " הצטרף/ה לקהילה!\n" +
                "גודל הקהילה כעת: " + communitySize + " חברים.";
    }

    public static String questionMessage(int questionNumber, int totalQuestions, String questionText) {
        return "📋 שאלה " + questionNumber + " מתוך " + totalQuestions + ":\n" + questionText;
    }

    public static String questionAnswered(int questionNumber, int totalQuestions, String questionText, String selectedOption) {
        return questionMessage(questionNumber, totalQuestions, questionText) +
                "\n\n✅ בחרת: " + selectedOption;
    }

    public static String answerAccepted() {
        return "✅ התשובה נקלטה!";
    }

    public static String alreadyAnswered() {
        return "⚠️ כבר ענית על שאלה זו.";
    }

    public static String surveyNotActive() {
        return "הסקר הזה כבר אינו פעיל.";
    }

    public static String surveyCompleted() {
        return "🎉 סיימת את כל שאלות הסקר! תודה על השתתפותך.";
    }

    public static String reminder(int answered, int total) {
        if (answered == 0) {
            return "⏰ תזכורת: עדיין לא התחלת לענות על הסקר הפעיל (" + total + " שאלות). עדיין יש זמן!";
        }
        return "⏰ תזכורת: ענית על " + answered + " מתוך " + total +
                " שאלות בסקר הפעיל. עדיין יש זמן להשלים!";
    }

    public static String surveyClosedThanksCompleted() {
        return "הסקר הסתיים. תודה שהשלמת אותו עד הסוף! 🙌";
    }

    public static String surveyClosedNotCompleted() {
        return "הסקר הסתיים ולא הספקת להשלים את כל השאלות. תודה על ההשתתפות - נתראה בסקר הבא!";
    }
}
