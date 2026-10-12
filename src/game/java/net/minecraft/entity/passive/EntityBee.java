package net.minecraft.entity.passive;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Treasure Client — "ViaEntities": the modern 1.15 Bee.
 *
 * A small passive mob registered into the 1.12 entity system (spawn it from its
 * creative egg). Simplified to ground-wander AI — the real bee flies — but
 * renders the modern creature end to end (ModelBee + texture). Mirrors
 * {@link EntityCopperGolem}.
 */
public class EntityBee extends EntityCreature {

	public EntityBee(World worldIn) {
		super(worldIn);
		this.setSize(0.7F, 0.6F);
	}

	protected void initEntityAI() {
		this.tasks.addTask(0, new EntityAISwimming(this));
		this.tasks.addTask(5, new EntityAIWanderAvoidWater(this, 1.0D));
		this.tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
		this.tasks.addTask(8, new EntityAILookIdle(this));
	}

	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(10.0D);
		this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
	}
}
