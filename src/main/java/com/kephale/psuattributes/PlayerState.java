package com.kephale.psuattributes;

public final class PlayerState {

    public int maceHits;
    public boolean maceEmpowered;
    public boolean maceAirborne;
    public int macePendingLevel = -1;
    public int maceCooldownUntil;
    public boolean maceCooling;

    public boolean spearAirChain;
    public int spearCooldownUntil;
    public boolean spearCooling;

    public int swordHits;
    public boolean swordActive;
    public int swordCooldownUntil;
    public boolean swordCooling;

    public void resetMace() {
        maceHits = 0;
        maceEmpowered = false;
        maceAirborne = false;
        macePendingLevel = -1;
        maceCooldownUntil = 0;
        maceCooling = false;
    }

    public void resetSpear() {
        spearAirChain = false;
        spearCooldownUntil = 0;
        spearCooling = false;
    }

    public void resetSword() {
        swordHits = 0;
        swordActive = false;
        swordCooldownUntil = 0;
        swordCooling = false;
    }
}
