package atomicstryker.infernalmobs.common.mods;

import atomicstryker.infernalmobs.common.InfernalMobsCore;
import atomicstryker.infernalmobs.common.MobModifier;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

public class MM_Darkness extends MobModifier
{
    
    public MM_Darkness()
    {
        super();
    }
    
    public MM_Darkness(MobModifier next)
    {
        super(next);
    }

    @Override
    public String getModName()
    {
        return "Darkness";
    }
    
    @Override
    public float onHurt(EntityLivingBase mob, DamageSource source, float damage)
    {
        if (source.getTrueSource() != null
        && (source.getTrueSource() instanceof EntityLivingBase)
        && InfernalMobsCore.instance().getIsEntityAllowedTarget(source.getTrueSource()))
        {
            ((EntityLivingBase)source.getTrueSource()).addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 120, 0));
        }
        
        return super.onHurt(mob, source, damage);
    }
    
    @Override
    public float onAttack(EntityLivingBase entity, DamageSource source, float damage)
    {
        if (entity != null
        && InfernalMobsCore.instance().getIsEntityAllowedTarget(entity))
        {
            entity.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 120, 0));
        }
        
        return super.onAttack(entity, source, damage);
    }
    
    @Override
    protected String[] getModNameSuffix()
    {
        return suffix;
    }
    private static String[] suffix = { "ofDarkness", "theShadow", "theEclipse" };
    
    @Override
    protected String[] getModNamePrefix()
    {
        return prefix;
    }
    private static String[] prefix = { "dark", "shadowkin", "eclipsed" };
    
}
