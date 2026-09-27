package dev.akumavote;

import java.util.List;

public record VoteSite(String name, String url) {
    public static final List<VoteSite> ALL = List.of(
            new VoteSite("CurseForge", "https://www.curseforge.com/servers/minecraft/game/akumamc-1/vote"),
            new VoteSite("MinecraftServers.org", "https://minecraftservers.org/vote/684524"),
            new VoteSite("ServersForMinecraft", "https://serversforminecraft.net/server/akumamc/vote"),
            new VoteSite("Minecraft-Servers.co", "https://minecraft-servers.co/#vote=565"),
            new VoteSite("Minecraft-MP", "https://minecraft-mp.com/server/354948/vote/"),
            new VoteSite("Minecraft.buzz", "https://minecraft.buzz/vote/19402"),
            new VoteSite("Minecraft Server List", "https://minecraft-server-list.com/server/470063/vote/"),
            new VoteSite("Best Minecraft Servers", "https://best-minecraft-servers.co/server-akumamc.5162/vote"),
            new VoteSite("Minerank", "https://www.minerank.com/akuma-network/vote"),
            new VoteSite("Play Minecraft Servers", "https://play-minecraft-servers.com/minecraft-servers/akumamc/?tab=vote"),
            new VoteSite("TopG", "https://topg.org/minecraft-servers/server-680720#vote"),
            new VoteSite("Minecraft Best Servers", "https://minecraftbestservers.com/server-akumamc-mujznr.6720/vote")
    );
}