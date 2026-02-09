package com.liante.client;

import com.liante.network.UnitInventoryPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientUnitManager {
    private static Map<String, Integer> ownedCounts = new HashMap<>();

    // 패킷 수신 시 전체 보유량 갱신
    public static void updateInventory(UnitInventoryPayload payload) {
        System.out.println("[MingTD-Manager] 데이터 교체 중... 이전 크기: " + ownedCounts.size() + " -> 새 크기: " + payload.inventory().size());


        ownedCounts = payload.inventory();
    }

    // RtsScreen에서 기존에 약속한 메서드 명칭 유지
    public static Map<String, Integer> getOwnedCounts() {
        return ownedCounts;
    }
}
