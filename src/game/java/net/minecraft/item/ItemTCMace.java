package net.minecraft.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;

/**
 * Treasure Client: the modern mace. Stats from 1.21 (6 attack damage, 0.6 attack
 * speed, 500 durability, no sweeping). The smash attack (extra damage from how far
 * you fell, knocking nearby players away) is done on the server by
 * {@link net.minecraft.tcpvp.TCMace} when it hits.
 */
public class ItemTCMace extends ItemSword {

	public ItemTCMace() {
		super(Item.ToolMaterial.DIAMOND);
		this.setMaxDamage(500);
	}

	public int getItemEnchantability() {
		return 15;
	}

	public Multimap<String, AttributeModifier> getItemAttributeModifiers(EntityEquipmentSlot equipmentSlot) {
		Multimap<String, AttributeModifier> multimap = HashMultimap.<String, AttributeModifier>create();
		if (equipmentSlot == EntityEquipmentSlot.MAINHAND) {
			multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getAttributeUnlocalizedName(),
					new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", 5.0D, 0));
			multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getAttributeUnlocalizedName(),
					new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", -3.4D, 0));
		}
		return multimap;
	}
}
