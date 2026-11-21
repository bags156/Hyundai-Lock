package io.github.bags156.hyundailock.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeywordRulesTest {

    private final KeywordRules defaults = new KeywordRules(KeywordRules.DEFAULT_PACKAGES,
            KeywordRules.DEFAULT_TRIGGERS, KeywordRules.DEFAULT_IGNORES);

    @Test
    public void watchesOnlyListedApps() {
        assertTrue(defaults.watches("com.stationdm.bluelink"));
        assertTrue(defaults.watches("COM.STATIONDM.BLUELINK"));
        assertFalse(defaults.watches("com.whatsapp"));
        assertFalse(defaults.watches(null));
    }

    @Test
    public void unlockedAlertsMatch() {
        assertEquals("unlock", defaults.matchedTrigger("Vehicle Alert · Your doors are unlocked"));
        assertTrue(defaults.matches("Remote Door Unlock request was successful"));
        assertTrue(defaults.matches("UNLOCKED"));
    }

    @Test
    public void lockedAndFailedAlertsDoNotMatch() {
        assertFalse(defaults.matches("Remote Door Lock request was successful"));
        assertFalse(defaults.matches("Your vehicle is locked"));
        assertFalse(defaults.matches("Remote Door Unlock request failed"));
        assertFalse(defaults.matches("We couldn’t unlock your vehicle"));
        assertFalse(defaults.matches("Could not unlock: vehicle out of range"));
        assertFalse(defaults.matches(""));
        assertFalse(defaults.matches(null));
    }

    @Test
    public void listsSplitOnCommasSemicolonsAndNewLines() {
        assertEquals(3, KeywordRules.parseList(" a, b;\nc ,, a ").size());
        assertTrue(KeywordRules.parseList(null).isEmpty());
        assertTrue(KeywordRules.parseList(" , ;").isEmpty());
    }

    @Test
    public void customPhrases() {
        KeywordRules rules = new KeywordRules("x.y", "doors left open, unlocked", "test");
        assertTrue(rules.matches("Doors left open on your Tucson"));
        assertFalse(rules.matches("Unlocked (test message)"));
        assertNull(rules.matchedTrigger("Engine started"));
    }

    @Test
    public void noTriggersMatchesNothing() {
        KeywordRules rules = new KeywordRules("x.y", "", "");
        assertFalse(rules.matches("unlocked"));
    }
}
