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
 * Treasure Client — "ViaEntities" proof of concept: the modern Copper Golem.
 *
 * A simple passive ground mob registered into the normal 1.12 entity system so
 * the client can render a modern entity (custom model + texture) end to end:
 * EntityList registration -> spawn egg -> RenderManager -> ModelCopperGolem ->
 * texture. Independent of the network side (spawn it with its creative egg).
 */
public class EntityCopperGolem extends EntityCreature {

	public EntityCopperGolem(World worldIn) {
		super(worldIn);
		this.setSize(0.9F, 1.4F);
	}

	protected void initEntityAI() {
		this.tasks.addTask(0, new EntityAISwimming(this));
		this.tasks.addTask(5, new EntityAIWanderAvoidWater(this, 0.7D));
		this.tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
		this.tasks.addTask(8, new EntityAILookIdle(this));
	}

	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
		this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25D);
	}
}
