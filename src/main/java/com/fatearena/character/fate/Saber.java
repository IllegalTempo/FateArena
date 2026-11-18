package com.fatearena.character.fate;

import com.fatearena.FateArenaPlugin;
import com.fatearena.animation.AnimationClip;
import com.fatearena.character.Character;
import com.fatearena.core.GameMob;
import com.fatearena.core.GamePlayer;

public class Saber extends Character {

    public Saber(GamePlayer player) {
        super("saber",player);
    }

    @Override
    protected String getSkinTextureUrl() {
        // URL decoded from the supplied MineSkin texture value.
        return "http://textures.minecraft.net/texture/a8e8d9be86268e096729233f6bf521334e8543afbc61320b4456d53b69e83990";
    }

    @Override
    public void autoAttack(GameMob target) {
        if (player.canAttack()) {
            player.getAnimationManager().playAttackAnimation("a1", 20);
        }
    }
}
