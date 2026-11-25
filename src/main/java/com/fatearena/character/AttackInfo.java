package com.fatearena.character;

public class AttackInfo {
    public int damage;
    public String animationName;
    public int attackDuration;
    public int attackDelayTicks;
    public final boolean useVanillaHead;
    public int manaCost;

    public AttackInfo(int damage, String animationName, int attackDuration, int attackDelayTicks, int manaCost) {
        this(damage, animationName, attackDuration, attackDelayTicks, manaCost,false);
    }

    public AttackInfo(int damage, String animationName, int attackDuration, int attackDelayTicks, int manaCost,boolean useVanillaHead) {
        this.damage = damage;
        this.animationName = animationName;
        this.attackDuration = attackDuration;
        this.attackDelayTicks = attackDelayTicks;
        this.useVanillaHead = useVanillaHead;
        this.manaCost = manaCost;
    }
}
