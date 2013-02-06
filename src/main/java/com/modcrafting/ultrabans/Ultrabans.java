/* COPYRIGHT (c) 2012 Joshua McCurry
 * This work is licensed under the
 * Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License
 * and use of this software or its code is an agreement to this license.
 * A full copy of this license can be found at
 * http://creativecommons.org/licenses/by-nc-sa/3.0/. 
 */
package com.modcrafting.ultrabans;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.h31ix.updater.Updater;
import net.h31ix.updater.Updater.UpdateResult;
import net.h31ix.updater.Updater.UpdateType;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.modcrafting.ultrabans.commands.Ban;
import com.modcrafting.ultrabans.commands.Check;
import com.modcrafting.ultrabans.commands.CheckIP;
import com.modcrafting.ultrabans.commands.DupeIP;
import com.modcrafting.ultrabans.commands.Edit;
import com.modcrafting.ultrabans.commands.Export;
import com.modcrafting.ultrabans.commands.Help;
import com.modcrafting.ultrabans.commands.History;
import com.modcrafting.ultrabans.commands.Import;
import com.modcrafting.ultrabans.commands.Ipban;
import com.modcrafting.ultrabans.commands.Kick;
import com.modcrafting.ultrabans.commands.Perma;
import com.modcrafting.ultrabans.commands.Reload;
import com.modcrafting.ultrabans.commands.Status;
import com.modcrafting.ultrabans.commands.Tempban;
import com.modcrafting.ultrabans.commands.Tempipban;
import com.modcrafting.ultrabans.commands.Unban;
import com.modcrafting.ultrabans.commands.Version;
import com.modcrafting.ultrabans.commands.Warn;
import com.modcrafting.ultrabans.db.Database;
import com.modcrafting.ultrabans.db.SQL;
import com.modcrafting.ultrabans.db.SQLite;
import com.modcrafting.ultrabans.listeners.UltraBanPlayerListener;
import com.modcrafting.ultrabans.util.EditBan;

public class Ultrabans extends JavaPlugin {

  public HashSet<String> bannedPlayers = new HashSet<String>();
  public HashSet<String> bannedIPs = new HashSet<String>();
  public Map<String, Long> tempBans = new HashMap<String, Long>();
  public Map<String, EditBan> banEditors = new HashMap<String, EditBan>();
  public Database db;
  public static final String ADMIN = "%admin%";
  public static final String REASON = "%reason%";
  public static final String VICTIM = "%victim%";
  public static final String AMOUNT = "%amt%";
  public static final String MODE = "%mode%";
  public static final String TIME = "%time%";
  public static String DEFAULT_ADMIN;
  public static String DEFAULT_REASON;
  public static String DEFAULT_DENY_MESSAGE;
  private static Ultrabans constant;
  private boolean log;

  @Override
  public void onDisable() {
    this.getServer().getScheduler().cancelTasks(this);
    tempBans.clear();
    bannedPlayers.clear();
    bannedIPs.clear();
    banEditors.clear();
  }

  @Override
  public void onEnable() {
    setConstant(this);
    long time = System.currentTimeMillis();
    this.getDataFolder().mkdir();
    this.saveDefaultConfig();
    FileConfiguration config = getConfig();
    log = config.getBoolean("Log.Enabled", true);
    if (config.getDouble("Config.Version") < Double.parseDouble(this.getDescription().getVersion().substring(0, 1))) {
      File file = new File(this.getDataFolder(), "/config.yml");
      if (!file.exists()) {
        this.saveDefaultConfig();
      }
    }
    DEFAULT_ADMIN = config.getString("Label.Console", "Server");
    DEFAULT_REASON = config.getString("Label.Reason", "Unsure");
    DEFAULT_DENY_MESSAGE = ChatColor.RED + config.getString("Messages.Permission", "You do not have the required permissions.");

    PluginManager pm = getServer().getPluginManager();
    pm.registerEvents(new UltraBanPlayerListener(this), this);
    loadCommands();

    PluginDescriptionFile pdf = this.getDescription();
    //Storage
    if (config.getString("Database").equalsIgnoreCase("mysql")) {
      db = new SQL(this);
    } else {
      db = new SQLite(this);
    }
    db.load();
    //Sync
    if (config.getBoolean("Sync.Enabled", false)) {
      long t = config.getLong("Sync.Timing", 72000L);
      this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
        @Override
        public void run() {
          onDisable();
          db.load();
        }
      }, 1, t);
    }
    //Updater
    if (config.getBoolean("AutoUpdater.Enabled", true)) {
      Updater up = new Updater(this, pdf.getName().toLowerCase(), this.getFile(), UpdateType.DEFAULT, true);
      if (!up.getResult().equals(UpdateResult.SUCCESS) || up.pluginFile(this.getFile().getName())) {
        if (up.getResult().equals(UpdateResult.FAIL_NOVERSION)) {
          this.getLogger().info("Unable to connect to dev.bukkit.org.");
        } else {
          this.getLogger().info("No Updates found on dev.bukkit.org.");
        }
      } else {
        this.getLogger().info("Update " + up.getLatestVersionString() + " found please restart your server.");
      }
    }
    this.getLogger().info("Loaded. " + ((long) (System.currentTimeMillis() - time) / 1000) + " secs.");
  }

  public void loadCommands() {
    getCommand("ban").setExecutor(new Ban(this));
    getCommand("checkban").setExecutor(new Check(this));
    getCommand("checkip").setExecutor(new CheckIP(this));
    getCommand("dupeip").setExecutor(new DupeIP(this));
    getCommand("editban").setExecutor(new Edit(this));
    getCommand("importbans").setExecutor(new Import(this));
    getCommand("exportbans").setExecutor(new Export(this));
    getCommand("uhelp").setExecutor(new Help());
    getCommand("ipban").setExecutor(new Ipban(this));
    getCommand("kick").setExecutor(new Kick(this));
    getCommand("ureload").setExecutor(new Reload(this));
    getCommand("tempban").setExecutor(new Tempban(this));
    getCommand("tempipban").setExecutor(new Tempipban(this));
    getCommand("unban").setExecutor(new Unban(this));
    getCommand("uversion").setExecutor(new Version(this));
    getCommand("warn").setExecutor(new Warn(this));
    getCommand("permaban").setExecutor(new Perma(this));
    getCommand("history").setExecutor(new History(this));
    getCommand("ustatus").setExecutor(new Status(this));
  }

  public static Ultrabans getPlugin() {
    return constant;
  }

  private static void setConstant(Ultrabans constant) {
    Ultrabans.constant = constant;
  }

  public Database getUBDatabase() {
    return db;
  }

  public boolean getLog() {
    return log;
  }
}
