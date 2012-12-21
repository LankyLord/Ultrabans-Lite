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

public class UltraBanPlayerListener implements Listener{
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
	public void onPlayerLogin(PlayerLoginEvent event){
		Player player = event.getPlayer();
		if(plugin.bannedPlayers.contains(player.getName().toLowerCase())){
			String reason = plugin.db.getBanReason(player.getName());
			String admin = plugin.db.getAdmin(player.getName());
			if(admin==null) admin = plugin.admin;
			if(reason==null) reason = plugin.reason;
			String bcmsg = config.getString("Messages.Ban.Login", "%admin% banned you from this server! Reason: %reason%!");
			if(bcmsg.contains(plugin.regexAdmin)) bcmsg = bcmsg.replaceAll(plugin.regexAdmin, admin);
			if(bcmsg.contains(plugin.regexReason)) bcmsg = bcmsg.replaceAll(plugin.regexReason, reason);
			bcmsg=plugin.util.formatMessage(bcmsg);
			event.disallow(PlayerLoginEvent.Result.KICK_BANNED, bcmsg);
		}
		if(plugin.tempBans.get(player.getName().toLowerCase()) != null){
			String reason = plugin.db.getBanReason(player.getName());
			String admin = plugin.db.getAdmin(player.getName());
			if(admin==null) admin = plugin.admin;
			if(reason==null) reason = plugin.reason;
			long tempTime = plugin.tempBans.get(player.getName().toLowerCase());
			long diff = tempTime - (System.currentTimeMillis()/1000);
			if(diff <= 0){
				String ip = plugin.db.getAddress(player.getName());
				if(plugin.bannedIPs.contains(ip)){
					plugin.bannedIPs.remove(ip);
					Bukkit.unbanIP(ip);
				}
				plugin.tempBans.remove(player.getName().toLowerCase());
				plugin.bannedPlayers.remove(player.getName().toLowerCase());
				plugin.db.removeFromBanlist(player.getName().toLowerCase());
				plugin.db.addPlayer(player.getName(), "Untempbanned: " + reason, admin, 0, 5);
				return;
			}
			Date date = new Date();
			date.setTime(tempTime*1000);
			String dateStr = date.toString();
			String msgvic = config.getString("Messages.TempBan.Login", "You have been tempbanned by %admin% for %time%. Reason: %reason%!");
			if(msgvic.contains(plugin.regexAdmin)) msgvic = msgvic.replaceAll(plugin.regexAdmin, admin);
			if(msgvic.contains(plugin.regexReason)) msgvic = msgvic.replaceAll(plugin.regexReason, reason);
			if(msgvic.contains("%time%")) msgvic = msgvic.replaceAll("%time%", dateStr.substring(4, 19));
			msgvic=plugin.util.formatMessage(msgvic);
			event.disallow(PlayerLoginEvent.Result.KICK_OTHER, msgvic);
			return;
		}
	}
	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlayerJoin(final PlayerJoinEvent event){
		final Player player = event.getPlayer();
		final String ip = player.getAddress().getAddress().getHostAddress();
		plugin.db.setAddress(player.getName().toLowerCase(), ip);
		if(plugin.bannedIPs.contains(ip)){
			event.setJoinMessage(null);
			String adminMsg = config.getString("Messages.IPBan.Login", "Your IP is banned!");
			adminMsg=plugin.util.formatMessage(adminMsg);
			player.kickPlayer(adminMsg);
		}
		if(!plugin.db.matchAddress(player.getName(), ip)){
			plugin.db.updateAddress(player.getName(), ip);
		}
		if(!player.hasPermission("ultraban.override.dupeip")&&config.getBoolean("Login.DupeCheck.Enable", true)){
			plugin.getServer().getScheduler().scheduleAsyncDelayedTask(plugin,new Runnable(){
				@Override
				public void run() {
					String ip = plugin.db.getAddress(player.getName());
					if(ip != null){
						List<String> list = plugin.db.listPlayers(ip);
						for(Player admin:plugin.getServer().getOnlinePlayers()){
							if(admin.hasPermission("ultraban.dupeip")){
								for(String name:list){
									if(!name.equalsIgnoreCase(player.getName())) admin.sendMessage(ChatColor.GRAY + "Player: " + name + " duplicates player: " + player.getName() + "!");
								}
							}
						}
					}
				}
			},20L);			
		}
		if(config.getBoolean("Login.PingCheck.Enable",true)){
			boolean p=false;

			int ping = 0;
			if(version.equals("v1_4_6")){
				ping = ((org.bukkit.craftbukkit.v1_4_6.entity.CraftPlayer) player).getHandle().ping;
			}else if(version.equals("v1_4_5")){
				ping = ((org.bukkit.craftbukkit.v1_4_5.entity.CraftPlayer) player).getHandle().ping;
			}else{
				ping = ((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle().ping;
			}
			p = checkPlayerPing(player, ping);
			for(Player admin:plugin.getServer().getOnlinePlayers()){
				if(admin.hasPermission("ultraban.ping")){
					if(p){
						admin.sendMessage(ChatColor.GRAY + "Player: " + player.getName() + " was kicked for High Ping!");
					}else{
						admin.sendMessage(ChatColor.GRAY + "Player: " + player.getName() + " Ping: "+String.valueOf(ping)+"ms");						
					}
					
				}
			}
		}
		if(config.getBoolean("Login.ProxyPingBack.Enable",false)){ //TODO UnderConstruction
			plugin.getServer().getScheduler().scheduleAsyncDelayedTask(plugin, new Runnable(){
				@Override
				public void run() {
					try {
						int to = config.getInt("Login.ProxyPingBack.Timeout",10000);
						InetAddress tip = InetAddress.getByName(ip);
					 	if(!tip.isReachable(to)){
					 		plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "kick "+event.getPlayer()+" Proxy.");
					 	}
					} catch (UnknownHostException e) {
				 		plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "kick "+event.getPlayer()+" Proxy.");
					} catch (IOException e) {
				 		plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "kick "+event.getPlayer()+" Proxy.");
					}
				}
				
			});
		}
		plugin.getLogger().info("Logged " + player.getName() + " connecting from ip:" + ip);
	}
	private boolean checkPlayerPing(Player player,int ping){
		int pingout =config.getInt("Login.PingCheck.MaxPing",500);
		if(ping>pingout&&!player.hasPermission("ultraban.override.pingcheck")){
			String msgvic = config.getString("Messages.Kick.MsgToVictim", "You have been kicked by %admin%. Reason: %reason%");
			if(msgvic.contains(plugin.regexAdmin)) msgvic = msgvic.replaceAll(plugin.regexAdmin, "Ultrabans");
			if(msgvic.contains(plugin.regexReason)) msgvic = msgvic.replaceAll(plugin.regexReason, "High Ping Rate");
			msgvic=plugin.util.formatMessage(msgvic);
			player.kickPlayer(msgvic);
			return true;
		}
		//pass
		return false;
	}
}