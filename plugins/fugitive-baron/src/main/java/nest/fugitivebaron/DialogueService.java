package nest.fugitivebaron;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

final class DialogueService {
    private static final String SPEAKER = "Baron";
    private final FugitiveBaronPlugin plugin;

    private long lineCooldownTicks;
    private long nextEligibleTick;
    private String subtitleMode;
    private boolean playVoiceAudio;
    private float voiceVolume;
    private float voicePitch;

    DialogueService(final FugitiveBaronPlugin plugin, final FileConfiguration config) {
        this.plugin = plugin;
        reload(config);
    }

    void reload(final FileConfiguration config) {
        this.lineCooldownTicks = config.getLong("encounter.line-cooldown-ticks", 80L);
        this.subtitleMode = config.getString("encounter.subtitle-mode", "both").toLowerCase();
        this.playVoiceAudio = config.getBoolean("encounter.play-voice-audio", true);
        this.voiceVolume = (float) config.getDouble("encounter.voice-volume", 1.0D);
        this.voicePitch = (float) config.getDouble("encounter.voice-pitch", 1.0D);
    }

    void speakTo(final Player player, final BaronState state, final long currentTick) {
        if (currentTick < nextEligibleTick) {
            return;
        }
        final List<DialogueLine> lines = DialogueManifest.linesFor(state);
        if (lines == null || lines.isEmpty()) {
            return;
        }

        final DialogueLine line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        deliverLine(player, line);
        nextEligibleTick = currentTick + lineCooldownTicks;
    }

    void forceLine(final Player player, final BaronState state, final long currentTick) {
        nextEligibleTick = 0L;
        speakTo(player, state, currentTick);
    }

    void deliverAmbientHideoutLine(final Player player, final String line, final long currentTick) {
        if (line == null || line.isBlank()) {
            return;
        }
        deliverSubtitle(player, SPEAKER, line);
        nextEligibleTick = currentTick + lineCooldownTicks;
    }

    void deliverAmbientHideoutLine(
        final Player player,
        final String line,
        final String soundId,
        final long currentTick
    ) {
        if (line == null || line.isBlank()) {
            return;
        }
        deliverSubtitle(player, SPEAKER, line);
        playSound(player, soundId);
        nextEligibleTick = currentTick + lineCooldownTicks;
    }

    void deliverSubtitle(final Player player, final String speaker, final String line) {
        final Component subtitle = Component.text("[" + speaker + "] ", NamedTextColor.GOLD)
            .append(Component.text(line, NamedTextColor.WHITE));

        if (!"chat".equals(subtitleMode)) {
            player.sendActionBar(subtitle);
        }
        if (!"actionbar".equals(subtitleMode)) {
            player.sendMessage(subtitle);
        }
    }

    private void deliverLine(final Player player, final DialogueLine line) {
        deliverSubtitle(player, SPEAKER, line.text());
        playSound(player, line.soundId());
        plugin.debugLog("Delivered line to " + player.getName() + ": " + line.soundId() + " :: " + line.text());
    }

    private void playSound(final Player player, final String soundId) {
        if (!playVoiceAudio || soundId == null || soundId.isBlank()) {
            return;
        }
        player.playSound(Sound.sound(Key.key(soundId), Sound.Source.VOICE, voiceVolume, voicePitch));
        plugin.debugLog("Played sound " + soundId + " for " + player.getName());
    }
}
