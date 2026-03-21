package nest.fugitivebaron;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class DialogueManifest {
    private static final Map<BaronState, List<DialogueLine>> STATE_LINES = new EnumMap<>(BaronState.class);

    static {
        STATE_LINES.put(BaronState.OBSERVE, List.of(
            new DialogueLine("baron.observe.observe_01", "Interesting. You are looking, but not pretending not to."),
            new DialogueLine("baron.observe.observe_02", "You have the expression of a man about to ask questions he cannot afford."),
            new DialogueLine("baron.observe.observe_03", "No one arrives here by accident. Not twice."),
            new DialogueLine("baron.observe.observe_04", "You look too sober to be harmless."),
            new DialogueLine("baron.observe.observe_05", "Either you are lost, or someone paid you to look this stupid."),
            new DialogueLine("baron.observe.observe_06", "If you came to discuss Techtonic finances, you should have brought a stronger drink.")
        ));
        STATE_LINES.put(BaronState.SUSPICIOUS, List.of(
            new DialogueLine("baron.suspicious.suspicious_01", "Too calm. That is always the first mistake."),
            new DialogueLine("baron.suspicious.suspicious_02", "Too tidy. Too calm. That is the posture of an informant."),
            new DialogueLine("baron.suspicious.suspicious_03", "I know an information predator when I see one."),
            new DialogueLine("baron.suspicious.suspicious_04", "Do not drift closer like some well-mannered assassin."),
            new DialogueLine("baron.suspicious.suspicious_05", "Back away unless you brought powder and excellent intentions."),
            new DialogueLine("baron.suspicious.suspicious_06", "Nobody approaches me that gently unless they are carrying a knife or a question."),
            new DialogueLine("baron.suspicious.suspicious_07", "I have survived friendlier faces and more ambitious creditors than you."),
            new DialogueLine("baron.suspicious.suspicious_08", "Every man with a badge believes subtlety is part of the uniform.")
        ));
        STATE_LINES.put(BaronState.TRUST, List.of(
            new DialogueLine("baron.trust.trust_01", "Ah. Now that is a civilized amount of degeneracy."),
            new DialogueLine("baron.trust.trust_02", "Good. You are not a narc, merely a bad influence."),
            new DialogueLine("baron.trust.trust_03", "Techtonic was never robbed. It was aggressively restructured."),
            new DialogueLine("baron.trust.trust_04", "Very well. We may trade in facts."),
            new DialogueLine("baron.trust.trust_05", "Your money did not vanish. It evolved into mobility."),
            new DialogueLine("baron.trust.trust_06", "You want repayment. I can offer perspective, memorabilia, and possibly a crate.")
        ));
        STATE_LINES.put(BaronState.FLEE, List.of(
            new DialogueLine("baron.panic.panic_01", "Nope. Fuck that. Too close."),
            new DialogueLine("baron.panic.panic_02", "No. Absolutely not. You have crossed from curious into intolerable."),
            new DialogueLine("baron.panic.panic_03", "Distance keeps everyone honest and me alive."),
            new DialogueLine("baron.panic.panic_04", "Do not lunge at me unless you want this meeting to become unnecessarily athletic."),
            new DialogueLine("baron.panic.panic_05", "Compromised. Entirely and spectacularly compromised."),
            new DialogueLine("baron.panic.panic_06", "Run first, explain later.")
        ));
        STATE_LINES.put(BaronState.ESCAPE, List.of(
            new DialogueLine("baron.escape.escape_01", "You have ruined a perfectly good conspiracy."),
            new DialogueLine("baron.escape.escape_02", "Congratulations. You turned a good hideout into a crime scene."),
            new DialogueLine("baron.escape.escape_03", "The money is gone, the books are fictional, and I am relocating.")
        ));
    }

    private DialogueManifest() {
    }

    static List<DialogueLine> linesFor(final BaronState state) {
        return STATE_LINES.getOrDefault(state, List.of());
    }
}
