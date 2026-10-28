package com.kephale.psuattributes;

import java.util.Locale;

public enum AttributeType {
    MACE,
    SPEAR,
    SWORD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AttributeType byId(String id) {
        for (AttributeType type : values()) {
            if (type.id().equalsIgnoreCase(id)) return type;
        }
        return null;
    }
}
