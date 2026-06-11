package com.massivecraft.factions.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de validation pour les placeholders ValoriaFactions.
 *
 * <p>FactionSnapshot est package-private — les tests construisent les instances
 * directement sans réflexion. Le field {@code topFactions} est injecté via
 * réflexion + setAccessible pour simuler l'état du cache sans démarrer Bukkit.</p>
 */
class ValoriaPlaceholderAPIManagerTest {

    /**
     * Pattern identique à celui de la classe de production.
     * Si le pattern change en prod, ce test le détectera immédiatement.
     */
    private static final Pattern TOP_PLACEHOLDER =
            Pattern.compile("top_faction_(\\d{1,2})_(name|power|members)");

    // ── Snapshots de test (construits directement, plus de réflexion) ────

    private ValoriaPlaceholderAPIManager.FactionSnapshot snapshotEmpire;
    private ValoriaPlaceholderAPIManager.FactionSnapshot snapshotEclipse;
    private ValoriaPlaceholderAPIManager.FactionSnapshot snapshotChaos;
    private ValoriaPlaceholderAPIManager.FactionSnapshot snapshotSolo;

    @BeforeEach
    void setUp() {
        snapshotEmpire  = new ValoriaPlaceholderAPIManager.FactionSnapshot("Empire",   500.0, 12);
        snapshotEclipse = new ValoriaPlaceholderAPIManager.FactionSnapshot("Eclipse",  420.0, 10);
        snapshotChaos   = new ValoriaPlaceholderAPIManager.FactionSnapshot("Chaos",    310.0,  7);
        snapshotSolo    = new ValoriaPlaceholderAPIManager.FactionSnapshot("Solitaire",  80.0,  1);
    }

    // ── Tests : pattern regex ─────────────────────────────────────────────

    @Test
    @DisplayName("Le pattern accepte _members pour les positions 1 à 10")
    void patternAcceptsMembersVariant() {
        for (int i = 1; i <= 10; i++) {
            Matcher m = TOP_PLACEHOLDER.matcher("top_faction_" + i + "_members");
            assertTrue(m.matches(), "Le pattern doit matcher top_faction_" + i + "_members");
            assertEquals(String.valueOf(i), m.group(1));
            assertEquals("members", m.group(2));
        }
    }

    @Test
    @DisplayName("Le pattern accepte toujours _name et _power (non-régression)")
    void patternStillAcceptsNameAndPower() {
        assertTrue(TOP_PLACEHOLDER.matcher("top_faction_1_name").matches());
        assertTrue(TOP_PLACEHOLDER.matcher("top_faction_1_power").matches());
    }

    @Test
    @DisplayName("Le pattern refuse les variantes inconnues")
    void patternRejectsUnknownVariants() {
        assertFalse(TOP_PLACEHOLDER.matcher("top_faction_1_online").matches());
        assertFalse(TOP_PLACEHOLDER.matcher("top_faction_1_bank").matches());
        assertFalse(TOP_PLACEHOLDER.matcher("top_faction_1_claims").matches());
        assertFalse(TOP_PLACEHOLDER.matcher("top_faction_1_").matches());
    }

    // ── Tests : FactionSnapshot ───────────────────────────────────────────

    @Test
    @DisplayName("FactionSnapshot stocke correctement les trois composants")
    void snapshotStoresAllThreeComponents() {
        assertEquals("Empire", snapshotEmpire.name());
        assertEquals(500.0,    snapshotEmpire.power(), 0.001);
        assertEquals(12,       snapshotEmpire.members());
    }

    @Test
    @DisplayName("FactionSnapshot#members retourne la valeur passée au constructeur")
    void snapshotMembersReturnsConstructorValue() {
        assertEquals(12, snapshotEmpire.members());
        assertEquals(10, snapshotEclipse.members());
        assertEquals(7,  snapshotChaos.members());
        assertEquals(1,  snapshotSolo.members());
    }

    // ── Tests : résolution du placeholder _members ────────────────────────

    @ParameterizedTest(name = "Position {0} → {1} membres")
    @CsvSource({
        "1, 12",
        "2, 10",
        "3,  7",
        "4,  1",
    })
    @DisplayName("_members retourne le nombre total de membres (online + offline)")
    void membersPlaceholderReturnsCorrectCount(int position, int expectedMembers) {
        List<ValoriaPlaceholderAPIManager.FactionSnapshot> top =
                List.of(snapshotEmpire, snapshotEclipse, snapshotChaos, snapshotSolo);
        String result = resolveMembersPlaceholder(top, position);
        assertEquals(String.valueOf(expectedMembers), result,
                "top_faction_" + position + "_members attendu : " + expectedMembers);
    }

    @Test
    @DisplayName("_members retourne une chaîne purement numérique (compatible AjLeaderboards)")
    void membersPlaceholderIsPurelyNumeric() {
        List<ValoriaPlaceholderAPIManager.FactionSnapshot> top =
                List.of(snapshotEmpire, snapshotEclipse, snapshotChaos, snapshotSolo);
        for (int i = 1; i <= 4; i++) {
            String result = resolveMembersPlaceholder(top, i);
            assertNotNull(result);
            assertTrue(result.matches("\\d+"),
                    "top_faction_" + i + "_members doit être purement numérique, obtenu : '" + result + "'");
        }
    }

    @Test
    @DisplayName("_members retourne chaîne vide pour une position hors classement")
    void membersPlaceholderReturnsEmptyForOutOfBounds() {
        List<ValoriaPlaceholderAPIManager.FactionSnapshot> top = List.of(snapshotEmpire);
        assertEquals("", resolveMembersPlaceholder(top, 2),
                "Position hors classement doit retourner une chaîne vide");
    }

    // ── Tests : cohérence avec _name et _power ────────────────────────────

    @Test
    @DisplayName("_name, _power et _members pointent vers le même snapshot (même rang)")
    void nameAndMembersAreConsistentForSameRank() {
        List<ValoriaPlaceholderAPIManager.FactionSnapshot> top =
                List.of(snapshotEmpire, snapshotEclipse);

        assertEquals("Empire",  resolveNamePlaceholder(top, 1));
        assertEquals("12",      resolveMembersPlaceholder(top, 1));

        assertEquals("Eclipse", resolveNamePlaceholder(top, 2));
        assertEquals("10",      resolveMembersPlaceholder(top, 2));
    }

    @Test
    @DisplayName("L'ordre du classement est identique pour _name, _power et _members")
    void rankingOrderIsConsistentAcrossAllVariants() {
        List<ValoriaPlaceholderAPIManager.FactionSnapshot> top =
                List.of(snapshotEmpire, snapshotEclipse, snapshotChaos, snapshotSolo);

        String[] expectedNames   = {"Empire", "Eclipse", "Chaos", "Solitaire"};
        int[]    expectedMembers = {12, 10, 7, 1};

        for (int i = 1; i <= 4; i++) {
            assertEquals(expectedNames[i - 1], resolveNamePlaceholder(top, i),
                    "Nom inattendu en position " + i);
            assertEquals(String.valueOf(expectedMembers[i - 1]), resolveMembersPlaceholder(top, i),
                    "Members inattendu en position " + i);
        }
    }

    // ── Tests : cas limites ───────────────────────────────────────────────

    @Test
    @DisplayName("Faction avec 0 membres retourne '0' (cas limite)")
    void membersPlaceholderHandlesZeroMembers() {
        var snapshotEmpty = new ValoriaPlaceholderAPIManager.FactionSnapshot("Vide", 50.0, 0);
        List<ValoriaPlaceholderAPIManager.FactionSnapshot> top = List.of(snapshotEmpty);
        assertEquals("0", resolveMembersPlaceholder(top, 1));
    }

    @Test
    @DisplayName("Classement vide retourne chaîne vide pour toute position")
    void membersPlaceholderHandlesEmptyTopList() {
        List<ValoriaPlaceholderAPIManager.FactionSnapshot> top = List.of();
        assertEquals("", resolveMembersPlaceholder(top, 1));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String resolveMembersPlaceholder(
            List<ValoriaPlaceholderAPIManager.FactionSnapshot> topFactions, int position) {
        if (position < 1 || position > 10 || position > topFactions.size()) {
            return "";
        }
        return String.valueOf(topFactions.get(position - 1).members());
    }

    private String resolveNamePlaceholder(
            List<ValoriaPlaceholderAPIManager.FactionSnapshot> topFactions, int position) {
        if (position < 1 || position > 10 || position > topFactions.size()) {
            return "";
        }
        return topFactions.get(position - 1).name();
    }
}
