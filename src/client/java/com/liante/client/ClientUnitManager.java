package com.liante.client;

import com.liante.MingtdUnit;
import com.liante.network.UnitInventoryPayload;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

import java.util.*;

public class ClientUnitManager {
    private static Map<String, Integer> ownedCounts = new HashMap<>();
    private static List<MingtdUnit> ownedList = new ArrayList<>();

    public static void updateInventory(UnitInventoryPayload payload, ClientWorld world) {
        Map<String, Integer> newCounts = new HashMap<>();
        List<MingtdUnit> newList = new ArrayList<>();

        // 서버에서 온 엔티티 ID 맵을 순회
        payload.unitEntityMap().forEach((unitId, entityIds) -> {
            newCounts.put(unitId, entityIds.size()); // 개수 저장

            for (int id : entityIds) {
                Entity entity = world.getEntityById(id);
                if (entity instanceof MingtdUnit unit) {
                    newList.add(unit); // 실제 객체 저장
                }
            }
        });

        ownedCounts = newCounts;
        ownedList = newList;

        System.out.println("[MingTD-Manager] 동기화 완료: 유닛 총 " + ownedList.size() + "마리");
    }

    public static Map<String, Integer> getOwnedCounts() { return ownedCounts; }
    public static List<MingtdUnit> getOwnedList() { return ownedList; }
}
