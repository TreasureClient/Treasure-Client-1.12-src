package net.minecraft.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import javax.annotation.Nullable;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * Treasure Client — modern (1.21.11+) spear.
 *
 * <p>How a modern server expects a spear to be used:
 * <ul>
 *   <li><b>Jab</b> (left click): the client sends player-action STAB (digging
 *       action 7) instead of an entity attack. The server ignores normal attack
 *       packets while a spear is held. Needs a full attack cooldown.</li>
 *   <li><b>Charge</b> (hold right click): the client starts using the item and
 *       keeps sprinting; the server deals damage from your speed. Releasing sends
 *       RELEASE_USE_ITEM.</li>
 * </ul>
 * Stats are copied from the modern {@code Items} / {@code Item.Properties.spear}.
 */
public class ItemSpear extends ItemSword {

	/** Seconds per jab (also the swing animation length). */
	public final float attackDuration;
	/** Ticks: raising the spear, then how long each charge effect lasts. */
	public final int delayTicks;
	public final int dismountTicks;
	public final int knockbackTicks;
	public final int damageTicks;
	/** Modern attack damage bonus of the material (player base damage is 1). */
	private final float damageBonus;
	public static final float FORWARD_MOVEMENT = 0.38F;

	/** Set by RenderItem while drawing a spear in a hand (picks the long "in hand" model). */
	public static boolean renderingInHand = false;

	public ItemSpear(Item.ToolMaterial material, float attackDuration, float delay, float dismountTime,
			float knockbackTime, float damageTime, float damageBonus) {
		super(material);
		this.attackDuration = attackDuration;
		this.delayTicks = (int) (delay * 20.0F);
		this.dismountTicks = (int) (dismountTime * 20.0F);
		this.knockbackTicks = (int) (knockbackTime * 20.0F);
		this.damageTicks = (int) (damageTime * 20.0F);
		this.damageBonus = damageBonus;
		this.addPropertyOverride(new ResourceLocation("tc_in_hand"), new IItemPropertyGetter() {
			public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {
				return ItemSpear.renderingInHand ? 1.0F : 0.0F;
			}
		});
	}

	/** Builds the spear for a modern item name, or returns null if it isn't a spear. */
	public static ItemSpear forName(String name) {
		if (name.equals("wooden_spear")) return new ItemSpear(Item.ToolMaterial.WOOD, 0.65F, 0.75F, 5.0F, 10.0F, 15.0F, 0.0F);
		if (name.equals("stone_spear")) return new ItemSpear(Item.ToolMaterial.STONE, 0.75F, 0.7F, 4.5F, 9.0F, 13.75F, 1.0F);
		if (name.equals("copper_spear")) return new ItemSpear(Item.ToolMaterial.STONE, 0.85F, 0.65F, 4.0F, 8.25F, 12.5F, 1.0F);
		if (name.equals("iron_spear")) return new ItemSpear(Item.ToolMaterial.IRON, 0.95F, 0.6F, 2.5F, 6.75F, 11.25F, 2.0F);
		if (name.equals("golden_spear")) return new ItemSpear(Item.ToolMaterial.GOLD, 0.95F, 0.7F, 3.5F, 8.5F, 13.75F, 0.0F);
		if (name.equals("diamond_spear")) return new ItemSpear(Item.ToolMaterial.DIAMOND, 1.05F, 0.5F, 3.0F, 6.5F, 10.0F, 3.0F);
		if (name.equals("netherite_spear")) return new ItemSpear(Item.ToolMaterial.NETHERITE, 1.15F, 0.4F, 2.5F, 5.5F, 8.75F, 4.0F);
		return null;
	}

	/** Swing animation length in ticks (modern STAB swing). */
	public int getSwingTicks() {
		return Math.max(1, (int) (this.attackDuration * 20.0F));
	}

	public static boolean isSpear(ItemStack stack) {
		return stack != null && !stack.func_190926_b() && stack.getItem() instanceof ItemSpear;
	}

	public EnumAction getItemUseAction(ItemStack stack) {
		return EnumAction.SPEAR;
	}

	public int getMaxItemUseDuration(ItemStack stack) {
		return 72000;
	}

	public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
		playerIn.setActiveHand(handIn);
		return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
	}

	public Multimap<String, AttributeModifier> getItemAttributeModifiers(EntityEquipmentSlot equipmentSlot) {
		Multimap<String, AttributeModifier> multimap = HashMultimap.<String, AttributeModifier>create();
		if (equipmentSlot == EntityEquipmentSlot.MAINHAND) {
			multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getAttributeUnlocalizedName(),
					new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", (double) this.damageBonus, 0));
			multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getAttributeUnlocalizedName(),
					new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier",
							(double) (1.0F / this.attackDuration) - 4.0D, 0));
		}
		return multimap;
	}
}
