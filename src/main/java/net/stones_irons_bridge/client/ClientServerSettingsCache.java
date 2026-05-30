package net.stones_irons_bridge.client;

/**
 * Ein extrem schneller, lokaler Zwischenspeicher für den Client.
 */
public class ClientServerSettingsCache {
    public static boolean useReagents = true;
    public static boolean promptAnswered = false;
    public static boolean isAdmin = false; // Wird vom Server beim Login gefüllt
}