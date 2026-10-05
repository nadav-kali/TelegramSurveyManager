package telegramsurveymanager.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import telegramsurveymanager.model.Question;
import telegramsurveymanager.model.QuestionResult;
import telegramsurveymanager.model.Survey;
import telegramsurveymanager.model.SurveyParticipant;
import telegramsurveymanager.model.SurveyStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises the survey lifecycle (section 7-10 of the spec) with short, injected
 * durations so the 3-minute-reminder / 5-minute-close behaviour can be verified
 * in milliseconds instead of waiting on the real clock.
 */
class SurveyServiceTest {

    private final List<SurveyService> servicesToShutdown = new ArrayList<>();

    @AfterEach
    void tearDown() {
        servicesToShutdown.forEach(SurveyService::shutdown);
    }

    private SurveyService newService(CommunityService communityService, Duration surveyDuration, Duration reminderDelay) {
        SurveyService service = new SurveyService(communityService, surveyDuration, reminderDelay);
        servicesToShutdown.add(service);
        return service;
    }

    private CommunityService communityWithMembers(int count) {
        CommunityService community = new CommunityService();
        for (int i = 1; i <= count; i++) {
            community.join(i, "Member" + i, "member" + i);
        }
        return community;
    }

    private Question question(String text, String... options) {
        return new Question(text, List.of(options));
    }

    // ---- canStartSurvey / createSurvey validation ----

    @Test
    void cannotStartBelowMinimumCommunitySize() {
        CommunityService community = communityWithMembers(2);
        SurveyService survey = newService(community, Duration.ofSeconds(5), Duration.ofSeconds(3));

        assertTrue(survey.canStartSurvey() != null);
        assertThrows(IllegalStateException.class,
                () -> survey.createSurvey(List.of(question("Q1", "A", "B")), 0));
    }

    @Test
    void canStartAtMinimumCommunitySize() {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofSeconds(5), Duration.ofSeconds(3));

        assertNull(survey.canStartSurvey());
    }

    @Test
    void rejectsTooManyOrTooFewQuestions() {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofSeconds(5), Duration.ofSeconds(3));

        assertThrows(IllegalArgumentException.class, () -> survey.createSurvey(List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> survey.createSurvey(List.of(
                question("Q1", "A", "B"), question("Q2", "A", "B"),
                question("Q3", "A", "B"), question("Q4", "A", "B")), 0));
    }

    @Test
    void rejectsInvalidOptionCount() {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofSeconds(5), Duration.ofSeconds(3));

        assertThrows(IllegalArgumentException.class, () -> survey.createSurvey(List.of(question("Q1", "OnlyOne")), 0));
        assertThrows(IllegalArgumentException.class,
                () -> survey.createSurvey(List.of(question("Q1", "A", "B", "C", "D", "E")), 0));
    }

    @Test
    void cannotStartSecondSurveyWhileOneIsActive() throws InterruptedException {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofSeconds(5), Duration.ofSeconds(3));

        CountDownLatch started = new CountDownLatch(1);
        survey.addListener(new SurveyListener() {
            @Override
            public void onSurveyStarted(Survey s) {
                started.countDown();
            }
        });

        survey.createSurvey(List.of(question("Q1", "A", "B")), 0);
        assertTrue(started.await(2, TimeUnit.SECONDS));

        assertThrows(IllegalStateException.class, () -> survey.createSurvey(List.of(question("Q2", "A", "B")), 0));
    }

    // ---- participant snapshot semantics ----

    @Test
    void onlyMembersPresentAtStartBecomeParticipants_lateJoinerIsExcluded() throws InterruptedException {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofSeconds(5), Duration.ofSeconds(3));

        CountDownLatch started = new CountDownLatch(1);
        List<Survey> startedSurveys = new CopyOnWriteArrayList<>();
        survey.addListener(new SurveyListener() {
            @Override
            public void onSurveyStarted(Survey s) {
                startedSurveys.add(s);
                started.countDown();
            }
        });

        survey.createSurvey(List.of(question("Q1", "A", "B")), 0);
        assertTrue(started.await(2, TimeUnit.SECONDS));

        // a new member joins AFTER the survey has already started
        community.join(999L, "LateJoiner", "late");

        Survey s = startedSurveys.get(0);
        assertEquals(3, s.getParticipantCount());
        assertNull(s.getParticipant(999L), "a member who joins after start must not become a participant");
    }

    // ---- answering ----

    @Test
    void recordAnswer_successThenRejectsSecondAnswerToSameQuestion() throws InterruptedException {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofSeconds(5), Duration.ofSeconds(3));
        Survey started = startImmediately(survey, question("Q1", "A", "B"), question("Q2", "A", "B"));

        SurveyService.AnswerResult first = survey.recordAnswer(started.getId(), 1L, 0, 0);
        SurveyService.AnswerResult repeat = survey.recordAnswer(started.getId(), 1L, 0, 1);

        assertEquals(SurveyService.AnswerResult.SUCCESS, first);
        assertEquals(SurveyService.AnswerResult.ALREADY_ANSWERED, repeat);
    }

    @Test
    void recordAnswer_unknownSurveyOrNonParticipantAreRejected() throws InterruptedException {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofSeconds(5), Duration.ofSeconds(3));
        Survey started = startImmediately(survey, question("Q1", "A", "B"));

        assertEquals(SurveyService.AnswerResult.SURVEY_NOT_FOUND,
                survey.recordAnswer("not-the-real-id", 1L, 0, 0));
        assertEquals(SurveyService.AnswerResult.NOT_A_PARTICIPANT,
                survey.recordAnswer(started.getId(), 404L, 0, 0));
    }

    // ---- early close when everyone finishes ----

    @Test
    void closesEarlyOnceEveryParticipantCompletesAllQuestions_andComputesSortedResults() throws InterruptedException {
        CommunityService community = communityWithMembers(3);
        // generous duration - we expect the EARLY close path, not the timeout, to fire
        SurveyService survey = newService(community, Duration.ofSeconds(10), Duration.ofSeconds(8));

        CountDownLatch closed = new CountDownLatch(1);
        List<List<QuestionResult>> capturedResults = new CopyOnWriteArrayList<>();
        survey.addListener(new SurveyListener() {
            @Override
            public void onSurveyClosed(Survey s, List<QuestionResult> results) {
                capturedResults.add(results);
                closed.countDown();
            }
        });

        Survey started = survey.createSurveyAndAwaitStart(List.of(question("Favorite color?", "Red", "Blue")));

        // 3 participants answer question 0: two vote "Red" (index 0), one votes "Blue" (index 1)
        survey.recordAnswer(started.getId(), 1L, 0, 0);
        survey.recordAnswer(started.getId(), 2L, 0, 0);
        survey.recordAnswer(started.getId(), 3L, 0, 1);

        assertTrue(closed.await(2, TimeUnit.SECONDS), "survey must close immediately once everyone finished");
        assertEquals(SurveyStatus.CLOSED, started.getStatus());

        QuestionResult result = capturedResults.get(0).get(0);
        assertEquals("Favorite color?", result.questionText());
        assertEquals("Red", result.options().get(0).text(), "most-voted option must come first");
        assertEquals(2, result.options().get(0).votes());
        assertEquals(1, result.options().get(1).votes());
        assertEquals(66.67, result.options().get(0).percentage(), 0.1);
        assertEquals(33.33, result.options().get(1).percentage(), 0.1);
    }

    @Test
    void doesNotCloseEarlyUntilEveryoneFinishedAllQuestions() throws InterruptedException {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofSeconds(10), Duration.ofSeconds(8));

        CountDownLatch closed = new CountDownLatch(1);
        survey.addListener(new SurveyListener() {
            @Override
            public void onSurveyClosed(Survey s, List<QuestionResult> results) {
                closed.countDown();
            }
        });

        Survey started = survey.createSurveyAndAwaitStart(List.of(
                question("Q1", "A", "B"), question("Q2", "A", "B")));

        // all 3 answer Q1 only - must NOT close even though every participant answered "something"
        survey.recordAnswer(started.getId(), 1L, 0, 0);
        survey.recordAnswer(started.getId(), 2L, 0, 0);
        survey.recordAnswer(started.getId(), 3L, 0, 0);

        assertFalse(closed.await(500, TimeUnit.MILLISECONDS),
                "must not close early just because everyone answered question 1 of 2");
        assertEquals(SurveyStatus.ACTIVE, started.getStatus());
    }

    // ---- reminder ----

    @Test
    void reminderFiresOnlyForParticipantsWhoHaveNotCompleted() throws InterruptedException {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofSeconds(5), Duration.ofMillis(300));

        CountDownLatch reminded = new CountDownLatch(1);
        List<List<SurveyParticipant>> capturedReminders = new CopyOnWriteArrayList<>();
        survey.addListener(new SurveyListener() {
            @Override
            public void onReminderDue(Survey s, List<SurveyParticipant> notCompleted) {
                capturedReminders.add(notCompleted);
                reminded.countDown();
            }
        });

        Survey started = survey.createSurveyAndAwaitStart(List.of(question("Q1", "A", "B")));
        // participant 1 finishes immediately; 2 and 3 never answer
        survey.recordAnswer(started.getId(), 1L, 0, 0);

        assertTrue(reminded.await(2, TimeUnit.SECONDS));
        List<Long> remindedIds = capturedReminders.get(0).stream()
                .map(p -> p.getMember().telegramUserId()).sorted().toList();
        assertEquals(List.of(2L, 3L), remindedIds);
    }

    @Test
    void reminderIsSuppressedWhenSurveyClosesEarlyBeforeReminderDelay() throws InterruptedException {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofSeconds(5), Duration.ofSeconds(2));

        CountDownLatch closed = new CountDownLatch(1);
        CountDownLatch reminded = new CountDownLatch(1);
        survey.addListener(new SurveyListener() {
            @Override
            public void onSurveyClosed(Survey s, List<QuestionResult> results) {
                closed.countDown();
            }

            @Override
            public void onReminderDue(Survey s, List<SurveyParticipant> notCompleted) {
                reminded.countDown();
            }
        });

        Survey started = survey.createSurveyAndAwaitStart(List.of(question("Q1", "A", "B")));
        survey.recordAnswer(started.getId(), 1L, 0, 0);
        survey.recordAnswer(started.getId(), 2L, 0, 0);
        survey.recordAnswer(started.getId(), 3L, 0, 0);

        assertTrue(closed.await(1, TimeUnit.SECONDS), "should close early, well before the 2s reminder delay");
        assertFalse(reminded.await(2500, TimeUnit.MILLISECONDS),
                "reminder must never fire once the survey already closed early");
    }

    // ---- auto-close on timeout ----

    @Test
    void autoClosesAfterSurveyDurationEvenIfNobodyAnswered() throws InterruptedException {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofMillis(300), Duration.ofSeconds(10));

        CountDownLatch closed = new CountDownLatch(1);
        survey.addListener(new SurveyListener() {
            @Override
            public void onSurveyClosed(Survey s, List<QuestionResult> results) {
                closed.countDown();
            }
        });

        survey.createSurveyAndAwaitStart(List.of(question("Q1", "A", "B")));

        assertTrue(closed.await(2, TimeUnit.SECONDS), "survey must auto-close once its duration elapses");
        assertEquals(SurveyStatus.CLOSED, survey.getCurrentSurvey().getStatus());
    }

    @Test
    void afterCloseANewSurveyCanBeCreated() throws InterruptedException {
        CommunityService community = communityWithMembers(3);
        SurveyService survey = newService(community, Duration.ofMillis(200), Duration.ofSeconds(10));

        survey.createSurveyAndAwaitStart(List.of(question("Q1", "A", "B")));
        waitUntilClosed(survey);

        assertNull(survey.canStartSurvey());
        survey.createSurvey(List.of(question("Q2", "A", "B")), 0);
    }

    // ---- helpers ----

    private Survey startImmediately(SurveyService survey, Question... questions) throws InterruptedException {
        return survey.createSurveyAndAwaitStart(List.of(questions));
    }

    private void waitUntilClosed(SurveyService survey) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            Survey current = survey.getCurrentSurvey();
            if (current != null && current.getStatus() == SurveyStatus.CLOSED) {
                return;
            }
            Thread.sleep(25);
        }
        fail("survey never reached CLOSED status");
    }
}
