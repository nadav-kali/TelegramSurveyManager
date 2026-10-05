# Telegram Survey Manager

מערכת לניהול סקרים בקרב קהילת משתמשים הרשומים לבוט Telegram, עם ממשק ניהול גרפי
ב-Java Swing. ראו את מפרט הדרישות המלא שסוכם מול המרצה לפרטי ההתנהגות הנדרשת.

## ארכיטקטורה

```
telegramsurveymanager/
  model/     POJOs טהורים (CommunityMember, Survey, Question, SurveyParticipant, QuestionResult...)
  service/   לוגיקה עסקית בלבד - CommunityService, SurveyService (timers, תזכורות, סגירה, תוצאות)
  service/ai/ אינטגרציית יצירת שאלות מבוססת AI, מאחורי interface (AiSurveyGenerator)
  bot/       SurveyBot - רק תרגום Telegram Update <-> קריאות ל-service, בלי לוגיקה עסקית
  gui/       DashboardGUI ופאנלים - רק Swing, מאזין ל-service events ומעדכן את המסך
  config/    טעינת הגדרות/טוקנים מ-config.properties
```

`service/*` לא תלוי ב-`bot` או ב-`gui`. גם הבוט וגם ה-GUI הם "לקוחות" של אותם שירותים
דרך ממשקי `CommunityListener`/`SurveyListener`, ולא מכירים אחד את השני.

## הרצה

1. התקינו JDK 25 ו-Maven (או פתחו את הפרויקט ישירות ב-IntelliJ IDEA, שמביא את שניהם).
2. העתיקו את `config.example.properties` ל-`src/main/resources/config.properties`
   ומלאו את טוקן הבוט וטוקן ה-AI proxy (קובץ זה ב-`.gitignore` ולא יעלה ל-git).
3. הריצו:
   ```bash
   mvn package
   java -jar target/TelegramSurveyManager.jar
   ```
   או פשוט הריצו את `telegramsurveymanager.Main` מתוך IntelliJ.
4. פתחו שיחה עם הבוט בטלגרם ושלחו `Hi` או `היי` כדי להצטרף לקהילה, ובמקביל צפו
   בממשק ה-Swing שנפתח (טבלת הקהילה אמורה להתעדכן מיידית).

## הערה על אבטחת סודות

הטוקנים (בוט הטלגרם ופרוקסי ה-AI) לא נכתבים בקוד - הם נטענים מ-
`src/main/resources/config.properties`, שאינו נכלל ב-git. מי שמשכפל את הריפו צריך
למלא קובץ כזה בעצמו לפי `config.example.properties`.
