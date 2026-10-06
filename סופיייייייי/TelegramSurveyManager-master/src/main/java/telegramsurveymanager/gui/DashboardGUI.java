package telegramsurveymanager.gui;

import telegramsurveymanager.model.CommunityMember;
import telegramsurveymanager.model.QuestionResult;
import telegramsurveymanager.model.Survey;
import telegramsurveymanager.model.SurveyParticipant;
import telegramsurveymanager.service.CommunityListener;
import telegramsurveymanager.service.CommunityService;
import telegramsurveymanager.service.SurveyListener;
import telegramsurveymanager.service.SurveyService;
import telegramsurveymanager.service.ai.AiSurveyGenerator;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Top-level window. Owns no business logic - it only wires itself as a
 * listener on the two services and forwards events to the right panel,
 * marshalled onto the EDT.
 */
public class DashboardGUI extends JFrame implements CommunityListener, SurveyListener {

    private static final String CARD_CREATE = "create";
    private static final String CARD_ACTIVE = "active";
    private static final String CARD_RESULTS = "results";

    private final CommunityPanel communityPanel;
    private final SurveyCreationPanel creationPanel;
    private final ActiveSurveyPanel activePanel;
    private final ResultsPanel resultsPanel;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainArea = new JPanel(cardLayout);

    public DashboardGUI(CommunityService communityService, SurveyService surveyService,
                         AiSurveyGenerator aiSurveyGenerator) {
        super("ניהול סקרי קהילה - Telegram Survey Manager");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(950, 600));
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        communityPanel = new CommunityPanel();
        creationPanel = new SurveyCreationPanel(surveyService, aiSurveyGenerator);
        activePanel = new ActiveSurveyPanel();
        resultsPanel = new ResultsPanel(() -> {
            creationPanel.resetForNewSurvey();
            cardLayout.show(mainArea, CARD_CREATE);
        });

        mainArea.add(creationPanel, CARD_CREATE);
        mainArea.add(activePanel, CARD_ACTIVE);
        mainArea.add(resultsPanel, CARD_RESULTS);

        // A plain BorderLayout instead of JSplitPane: CENTER always gets all remaining
        // width regardless of component orientation, so the survey-creation area (which
        // needs the room - it has multiple question/option fields) can never end up
        // squeezed the way it did under JSplitPane + RIGHT_TO_LEFT.
        communityPanel.setPreferredSize(new Dimension(360, 10));
        JPanel root = new JPanel(new BorderLayout());
        root.add(communityPanel, BorderLayout.LINE_END);
        root.add(mainArea, BorderLayout.CENTER);
        setContentPane(root);

        communityService.addListener(this);
        surveyService.addListener(this);

        communityPanel.refresh(communityService.getMembers());

        // The whole app is Hebrew - mirror every component (text alignment, table
        // column order, button/field order) to a natural right-to-left reading flow.
        applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        cardLayout.show(mainArea, CARD_CREATE);
    }

    @Override
    public void onMemberJoined(CommunityMember newMember, List<CommunityMember> allMembers) {
        SwingUtilities.invokeLater(() -> {
            communityPanel.refresh(allMembers);
            creationPanel.refreshState();
        });
    }

    @Override
    public void onSurveyScheduled(Survey survey) {
        SwingUtilities.invokeLater(() -> {
            activePanel.showScheduled(survey);
            cardLayout.show(mainArea, CARD_ACTIVE);
        });
    }

    @Override
    public void onSurveyStarted(Survey survey) {
        SwingUtilities.invokeLater(() -> {
            activePanel.showActive(survey);
            cardLayout.show(mainArea, CARD_ACTIVE);
        });
    }

    @Override
    public void onProgressChanged(Survey survey, SurveyParticipant participant) {
        SwingUtilities.invokeLater(() -> activePanel.updateProgress(survey));
    }

    @Override
    public void onSurveyClosed(Survey survey, List<QuestionResult> results) {
        SwingUtilities.invokeLater(() -> {
            activePanel.stop();
            resultsPanel.showResults(results);
            cardLayout.show(mainArea, CARD_RESULTS);
        });
    }
}
