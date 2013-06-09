/* COPYRIGHT (c) 2012 Joshua McCurry
 * This work is licensed under the
 * Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License
 * and use of this software or its code is an agreement to this license.
 * A full copy of this license can be found at
 * http://creativecommons.org/licenses/by-nc-sa/3.0/. 
 */
package com.modcrafting.ultrabans.listeners;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import com.modcrafting.ultrabans.Ultrabans;
import com.modcrafting.ultrabans.util.Formatting;

public class UltraBanPlayerListener implements Listener {

    Ultrabans plugin;
    String spamcheck = null;
    int spamCount = 0;
    FileConfiguration config;
    String version;

    public UltraBanPlayerListener(Ultrabans ultraBans) {
        plugin = ultraBans;
        config = ultraBans.getConfig();

        String p2 = plugin.getServer().getClass().getPackage().getName();
        version = p2.substring(p2.lastIndexOf('.') + 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        if (plugin.bannedPlayers.contains(player.getName().toLowerCase())) {
            String reason = plugin.getUBDatabase().getBanReason(player.getName());
            String admin = plugin.getUBDatabase().getAdmin(player.getName());
            if (admin == null)
                admin = Ultrabans.DEFAULT_ADMIN;
            if (reason == null)
                reason = Ultrabans.DEFAULT_REASON;
            String bcmsg = config.getString("Messages.Ban.Login", "%admin% banned you from this server! Reason: %reason%!");
            if (bcmsg.contains(Ultrabans.ADMIN))
                bcmsg = bcmsg.replaceAll(Ultrabans.ADMIN, admin);
            if (bcmsg.contains(Ultrabans.REASON))
                bcmsg = bcmsg.replaceAll(Ultrabans.REASON, reason);
            bcmsg = Formatting.formatMessage(bcmsg);
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, bcmsg);
        }
        if (plugin.tempBans.get(player.getName().toLowerCase()) != null) {
            String reason = plugin.getUBDatabase().getBanReason(player.getName());
            String admin = plugin.getUBDatabase().getAdmin(player.getName());
            if (admin == null)
                admin = Ultrabans.DEFAULT_ADMIN;
            if (reason == null)
                reason = Ultrabans.DEFAULT_REASON;
            long tempTime = plugin.tempBans.get(player.getName().toLowerCase());
            long diff = tempTime - (System.currentTimeMillis() / 1000);
            if (diff <= 0) {
                String ip = plugin.getUBDatabase().getAddress(player.getName());
                if (plugin.bannedIPs.contains(ip)) {
                    plugin.bannedIPs.remove(ip);
                    Bukkit.unbanIP(ip);
                }
                plugin.tempBans.remove(player.getName().toLowerCase());
                plugin.bannedPlayers.remove(player.getName().toLowerCase());
                plugin.getUBDatabase().removeFromBanlist(player.getName().toLowerCase());
                plugin.getUBDatabase().addPlayer(player.getName(), "Untempbanned: " + reason, admin, 0, 5);
                return;
            }
            Date date = new Date();
            date.setTime(tempTime * 1000);
            String dateStr = date.toString();
            String msgvic = config.getString("Messages.TempBan.Login", "You have been tempbanned by %admin% for %time%. Reason: %reason%!");
            if (msgvic.contains(Ultrabans.ADMIN))
                msgvic = msgvic.replaceAll(Ultrabans.ADMIN, admin);
            if (msgvic.contains(Ultrabans.REASON))
                msgvic = msgvic.replaceAll(Ultrabans.REASON, reason);
            if (msgvic.contains(Ultrabans.TIME))
                msgvic = msgvic.replaceAll(Ultrabans.TIME, dateStr.substring(4, 19));
            msgvic = Formatting.formatMessage(msgvic);
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, msgvic);
            return;
        }
        if (config.getBoolean("Lockdown", false) && !player.hasPermission("ultraban.override.lockdown")) {
            String lockMsgLogin = config.getString("Messages.Lockdown.LoginMsg", "Server is under a lockdown, Try again later!");
            lockMsgLogin = Formatting.formatMessage(lockMsgLogin);
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, lockMsgLogin);
            plugin.getLogger().info(player.getName() + " attempted to join during lockdown.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final String ip = player.getAddress().getAddress().getHostAddress();
        plugin.getUBDatabase().setAddress(player.getName().toLowerCase(), ip);
        if (plugin.bannedIPs.contains(ip)) {
            event.setJoinMessage(null);
            String adminMsg = config.getString("Messages.IPBan.Login", "Your IP is banned!");
            adminMsg = Formatting.formatMessage(adminMsg);
            player.kickPlayer(adminMsg);
        }
        if (!plugin.getUBDatabase().matchAddress(player.getName(), ip))
            plugin.getUBDatabase().updateAddress(player.getName(), ip);
        if (!player.hasPermission("ultraban.override.dupeip") && config.getBoolean("Login.DupeCheck.Enable", true))
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                @Override
                public void run() {
                    String ip = plugin.getUBDatabase().getAddress(player.getName());
                    if (ip != null) {
                        List<String> list = plugin.getUBDatabase().listPlayers(ip);
                        for (Player admin : plugin.getServer().getOnlinePlayers())
                            if (admin.hasPermission("ultraban.dupeip"))
                                for (String name : list)
                                    if (!name.equalsIgnoreCase(player.getName()))
                                        admin.sendMessage(ChatColor.GRAY + "Player: " + name + " duplicates player: " + player.getName() + "!");
                    }
                }
            }, 20L);
        if (config.getBoolean("Login.ProxyPingBack.Enable", false)) //TODO UnderConstruction
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                @Override
                public void run() {
                    try {
                        int to = config.getInt("Login.ProxyPingBack.Timeout", 10000);
                        InetAddress tip = InetAddress.getByName(ip);
                        if (!tip.isReachable(to))
                            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "kick " + event.getPlayer() + " Proxy.");
                    } catch (UnknownHostException e) {
                        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "kick " + event.getPlayer() + " Proxy.");
                    } catch (IOException e) {
                        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "kick " + event.getPlayer() + " Proxy.");
                    }
                }
            });
        plugin.getLogger().info("Logged " + player.getName() + " connecting from IP: " + ip);
    }
}
