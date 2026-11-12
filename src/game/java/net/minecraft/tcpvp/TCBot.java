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
	/** Duel bots: a totem saves them every time, without needing one in hand. */
	public boolean infiniteTotems;
	/** Duel bots: whether they were set to block with a shield. */
	public boolean duelShield;

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
		hideName(world, name);
		server.getPlayerList().playerLoggedIn(bot);
		bot.setLocationAndAngles(x, y, z, yaw, pitch);
		bot.rotationYawHead = yaw;
		server.getPlayerList().serverUpdateMovingPlayer(bot);
		return bot;
	}

	/** Bots are on a team whose name tags are never shown, so they have no name over their head. */
	private static void hideName(WorldServer world, String name) {
		net.minecraft.scoreboard.Scoreboard sb = world.getScoreboard();
		net.minecraft.scoreboard.ScorePlayerTeam team = sb.getTeam("tcbots");
		if (team == null) {
			team = sb.createTeam("tcbots");
		}
		team.setNameTagVisibility(net.minecraft.scoreboard.Team.EnumVisible.NEVER);
		sb.addPlayerToTeam(name, "tcbots");
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

	/** No knockback setting: hits don't push the bot. */
	public boolean tcNoKnockback() {
		return TCHerobot.botNoKnockback(this.world);
	}

	@Override
	public void knockBack(net.minecraft.entity.Entity attacker, float strength, double xRatio, double zRatio) {
		if (!tcNoKnockback()) {
			super.knockBack(attacker, strength, xRatio, zRatio);
		}
	}

	public void onDeath(DamageSource cause) {
		super.onDeath(cause);
		this.actions.onDeath();
	}
}
