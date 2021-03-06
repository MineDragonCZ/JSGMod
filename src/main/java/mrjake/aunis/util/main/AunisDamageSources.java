package mrjake.aunis.util.main;

import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;

public class AunisDamageSources {
	public static final DamageSource DAMAGE_EVENT_HORIZON = new DamageSource("eventHorizon")
			.setDamageIsAbsolute().setDamageBypassesArmor();
	public static final DamageSource DAMAGE_WRONG_SIDE = new DamageSource("wrongSide")
			.setDamageIsAbsolute().setDamageBypassesArmor();
	public static final DamageSource DAMAGE_EVENT_IRIS_CREATIVE = new DamageSource("irisDeath")
			.setDamageIsAbsolute().setDamageBypassesArmor().setDamageAllowedInCreativeMode();

	public static DamageSource getDamageSourceZat(Entity source, Entity attacker){
		return new EntityDamageSourceIndirect("zatHit", source, attacker)
				.setDamageBypassesArmor().setMagicDamage().setProjectile();
	}

	public static DamageSource getDamageSourceStaff(Entity source, Entity attacker){
		return new EntityDamageSourceIndirect("staffHit", source, attacker)
				.setDamageBypassesArmor().setExplosion().setFireDamage().setProjectile();
	}
}
