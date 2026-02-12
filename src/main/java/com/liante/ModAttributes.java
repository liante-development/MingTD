package com.liante;

import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.registry.entry.RegistryEntry;

public class ModAttributes {

    // 1. 물리 방어력 정의 (최소 0, 기본 0, 최대 1024)
    public static final RegistryEntry<EntityAttribute> PHYSICAL_DEFENSE = register(
            "physical_defense",
            new ClampedEntityAttribute("attribute.name.mingtd.physical_defense", 0.0, 0.0, 1024.0).setTracked(true)
    );

    // 2. 마법 방어력 정의
    public static final RegistryEntry<EntityAttribute> MAGIC_DEFENSE = register(
            "magic_defense",
            new ClampedEntityAttribute("attribute.name.mingtd.magic_defense", 0.0, 0.0, 1024.0).setTracked(true)
    );

    // 등록 메서드
    private static RegistryEntry<EntityAttribute> register(String id, EntityAttribute attribute) {
        return Registry.registerReference(Registries.ATTRIBUTE, Identifier.of("mingtd", id), attribute);
    }

    // 초기화 메서드 (Main 클래스에서 호출용)
    public static void registerAttributes() {
        // 클래스가 로드되면서 static 필드들이 등록됨
    }

    public static void addDefaultAttributes(DefaultAttributeContainer.Builder builder) {
        builder.add(PHYSICAL_DEFENSE);
        builder.add(MAGIC_DEFENSE);
    }
}
