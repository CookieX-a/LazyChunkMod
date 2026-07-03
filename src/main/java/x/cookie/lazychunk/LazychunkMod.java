/*
 *	MCreator note:
 *
 *	If you lock base mod element files, you can edit this file and the proxy files
 *	and they won't get overwritten. If you change your mod package or modid, you
 *	need to apply these changes to this file MANUALLY.
 *
 *
 *	If you do not lock base mod element files in Workspace settings, this file
 *	will be REGENERATED on each build.
 *
 */
package x.cookie.lazychunk;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Server;

public class LazychunkMod extends JavaPlugin {
	public static JavaPlugin plugin;
	public static Server server;

	@Override
	public void onEnable() {
		plugin = this;
		server = this.getServer();

	}

	@Override
	public void onDisable() {
	}
}