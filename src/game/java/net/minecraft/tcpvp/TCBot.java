package net.minecraft.tcpvp;

import com.mojang.authlib.GameProfile;

import net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageConstants;
import net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageProtocol;
import net.lax1dude.eaglercraft.socket.protocol.client.GameProtocolMessageController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

/**
 * Treasure Client: a practice bot, a server-side player with no real connection
 * (idea from the HeroBot mod by HerobaneNair, CC BY-NC-SA 4.0; written fresh here).
 *
 * <p>It is a normal player for the game: it takes damage, knockback, armor and
 * enchantments, blocks with shields, eats, and is shown with the player model.
 * Its "keyboard and mouse" are {@link TCBotActions}, set by the /player command.
 */
public class TCBot extends EntityPlayerMP {

	public final TCBotActions actions = new TCBotActions(this);

	/** Where the bot respawns. */
	public double homeX, homeY, homeZ;
	public float homeYaw, homePitch;
	/** The kit it was given last (given again on respawn), or null. */
	public String kit;

	private boolean respawnQueued;

	public TCBot(MinecraftServer server, WorldServer world, GameProfile profile) {
		super(server, world, profile, new PlayerInteractionManager(world));
	}

	/** Puts a new bot into the world (like a player joining). */
	public static TCBot spawn(MinecraftServer server, WorldServer world, String name, double x, double y, double z,
			float yaw, float pitch, boolean announce) {
		GameProfile profile = new GameProfile(EntityPlayer.getOfflineUUID(name), name);
		TCBot bot = new TCBot(server, world, profile);
		bot.setWorld(world);
		bot.interactionManager.setWorld(world);
		bot.interactionManager.setGameType(GameType.SURVIVAL);
		bot.homeX = x;
		bot.homeY = y;
		bot.homeZ = z;
		bot.homeYaw = yaw;
		bot.homePitch = pitch;
		bot.setLocationAndAngles(x, y, z, yaw, pitch);
		bot.rotationYawHead = yaw;
		final NetHandlerPlayServer handler = new NetHandlerPlayServer(server, new TCBotNet(name), bot);
		int ver = hostProtocol(server);
		if (ver > 0) {
			try {
				handler.setEaglerMessageController(new GameProtocolMessageController(
						GamePluginMessageProtocol.getByVersion(ver), GamePluginMessageConstants.SERVER_TO_CLIENT,
						GameProtocolMessageController.createServerHandler(ver, handler),
						(ch, msg) -> handler.sendPacket(new SPacketCustomPayload(ch, msg))));
			} catch (Throwable t) {
				// skins and capes are asked for by the real player, the bot doesn't need this
			}
		}
		if (announce) {
			TextComponentTranslation msg = new TextComponentTranslation("multiplayer.player.joined",
					new Object[] { bot.getDisplayName() });
			msg.getStyle().setColor(TextFormatting.YELLOW);
			server.getPlayerList().sendChatMsg(msg);
		}
		server.getPlayerList().playerLoggedIn(bot);
		bot.setLocationAndAngles(x, y, z, yaw, pitch);
		bot.rotationYawHead = yaw;
		server.getPlayerList().serverUpdateMovingPlayer(bot);
		return bot;
	}

	private static int hostProtocol(MinecraftServer server) {
		for (EntityPlayerMP p : server.getPlayerList().getPlayerList()) {
			if (!(p instanceof TCBot) && p.connection != null && p.connection.getEaglerMessageProtocol() != null) {
				return p.connection.getEaglerMessageProtocol().ver;
			}
		}
		return -1;
	}

	public void onUpdate() {
		super.onUpdate();
		if (this.isDead) {
			return;
		}
		if (this.getHealth() > 0.0F) {
			this.actions.tick();
		}
		double y0 = this.posY;
		this.onUpdateEntity(); // the movement tick a real player's connection would run
		if (!this.isDead && !this.isRiding()) {
			this.handleFalling(this.posY - y0, this.onGround);
		}
		if (!this.isDead) {
			this.mcServer.getPlayerList().serverUpdateMovingPlayer(this);
		}
		if (this.getHealth() <= 0.0F && this.deathTime >= 15 && !this.respawnQueued) {
			this.respawnQueued = true;
			TCBots.queueRespawn(this);
		}
	}

	public void onDeath(DamageSource cause) {
		super.onDeath(cause);
		this.actions.onDeath();
	}
}
