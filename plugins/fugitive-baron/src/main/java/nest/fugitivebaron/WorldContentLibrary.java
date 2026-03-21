package nest.fugitivebaron;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

final class WorldContentLibrary {
    private WorldContentLibrary() {
    }

    static List<BoardVariant> boardVariants() {
        return List.of(
            new BoardVariant("Village Well Board", "BARON WATCH",
                "Bought redstone, maps, and enough liquor to alarm a priest.",
                "If he says this is temporary, lock your chest immediately.",
                "He may attempt to repay you with prestige. Decline politely.",
                "Smelled like whiskey and panic. Paid in nonsense."
            ),
            new BoardVariant("Dock Board", "NOTICE: WHITE-SUITED MAN",
                "Passed through at dusk carrying paper, powder, and somebody else's money.",
                "Do not extend him credit unless payment in certificates sounds attractive.",
                "Reward available, though likely in lies and merchandise.",
                "Kept saying the money had become infrastructure."
            ),
            new BoardVariant("Frontier Outpost Board", "QUESTIONS ABOUT THE RIDGE",
                "Headed uphill muttering about signals and creditors.",
                "Do not follow him alone after dark unless you enjoy hills and bad news.",
                "He insists this is not hiding.",
                "Stared at the sky for ten minutes and tipped badly."
            ),
            new BoardVariant("Custom Town Board", "RUMORS, DEBTS & SIGHTINGS",
                "Asked whether bells could be wired for surveillance.",
                "Do not let him inspect your weather vane, software, or books.",
                "If found, return him to accounting by force or sarcasm.",
                "Called the weather hostile software."
            ),
            new BoardVariant("Harbor Market Board", "POSTINGS OF UNCERTAIN LEGAL VALUE",
                "White suit, bad sleep, worse ideas. Still armed with confidence.",
                "If he asks for copper and silence, give him neither.",
                "Debt collectors encouraged. Philosophers discouraged.",
                "Asked if my bell was wired. I said no. He did not believe me."
            ),
            new BoardVariant("Church Steps Board", "BARON WATCH",
                "Seen near the ridge with copper rods and three bottles.",
                "Keep your distance unless carrying the powder he seems to respect.",
                "If he offers equity, run.",
                "He said this was not hiding, merely reallocating geography."
            )
        );
    }

    static ItemStack antennaCoreBook() {
        return writtenBook(
            "To The Men Still Pretending This Is About Accounting",
            List.of(
                "If you found this room, then one of two things has happened: either I underestimated you, or you were guided here by inferior men.",
                "Both possibilities offend me. Techtonic was not robbed. It was liberated from static management by the only investor involved who understood velocity.",
                "Danger, disappearance, and speed all cost money. Your complaint is that I spent it first. That is not morality. That is poor timing on your part.",
                "You call it theft because you remain emotionally attached to numbers that no longer exist. I call it survival with better branding.",
                "If you want an explanation, sit in the chair, pour something honest, and look at the horizon until your moralism passes."
            )
        );
    }

    static ItemStack softwareBook() {
        return writtenBook(
            "Never Trust Someone Else's Software",
            List.of(
                "Never trust someone else's software, someone else's accountant, or someone else's weather report.",
                "All three are instruments for betrayal rendered in different interfaces.",
                "If a machine tells you it is safe, the machine is already compromised.",
                "If a man tells you his software is safe, he is already lying.",
                "And if he says the update is mandatory, leave through the nearest wall."
            )
        );
    }

    static ItemStack liquorBook() {
        return writtenBook(
            "On Liquor, Noise, and Detection",
            List.of(
                "A sensible quantity of liquor improves judgment.",
                "An excessive quantity improves courage. Somewhere between the two lies the only clear state in which a hunted man can hear the truth in the air.",
                "The hill speaks at night. The rods tick. The copper hums.",
                "The cowards below call this paranoia because they are too spiritually sedentary to recognize signal saturation."
            )
        );
    }

    static ItemStack repaymentCertificate() {
        return writtenBook(
            "Techtonic Platinum Investor Certificate",
            List.of(
                "This document certifies that the bearer retains a spiritually significant stake in Techtonic.",
                "The bearer may, in future conditions favorable to civilization, be considered repaid in prestige, influence, and commemorative standing.",
                "Cash settlement is postponed pending stability, sobriety, and the decline of hostile institutions.",
                "Congratulations on remaining notionally liquid."
            )
        );
    }

    static ItemStack techtonicSummaryPaper() {
        return namedPaper("Techtonic Asset Mobility Summary");
    }

    static ItemStack escapeNotePaper() {
        return namedPaper("If I Leave In A Hurry");
    }

    static ItemStack sponsorDraftPaper() {
        return namedPaper("Techtonic Sponsor Read Draft");
    }

    static ItemStack listenerNumbersPaper() {
        return namedPaper("Episode 34 Listener Numbers");
    }

    static ItemStack namedPaper(final String name) {
        final ItemStack item = new ItemStack(Material.PAPER);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack writtenBook(final String title, final List<String> pages) {
        final ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        final BookMeta meta = (BookMeta) item.getItemMeta();
        meta.setAuthor("The Baron");
        meta.setTitle(title);
        final List<Component> pageComponents = new ArrayList<>();
        for (final String page : pages) {
            pageComponents.add(Component.text(page));
        }
        meta.pages(pageComponents);
        item.setItemMeta(meta);
        return item;
    }

    record BoardVariant(String name, String header, String sighting, String warning, String comic, String witness) {
    }
}
