package com.liante.unit;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.liante.config.DefenseConfig; // AttackType 위치에 맞게 수정
import com.liante.recipe.UpgradeRecipe;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UnitDataLoader implements SynchronousResourceReloader {
    private static final Gson GSON = new Gson();

    @Override
    public void reload(ResourceManager manager) {
        // 기존 레지스트리 초기화 (리로딩 대응)
        MingtdUnits.UNIT_REGISTRY.clear();
        MingtdUnits.RECIPES_BY_INGREDIENT.clear();

        // data/mingtd/units 폴더 내의 모든 json 검색
        var resources = manager.findResources("units", path -> path.getPath().endsWith(".json"));

        resources.forEach((id, resource) -> {
            try (InputStreamReader reader = new InputStreamReader(resource.getInputStream())) {
                // 1. JSON 배열을 임시 DTO 리스트로 파싱
                List<UnitJsonDTO> dtoList = GSON.fromJson(reader, new TypeToken<List<UnitJsonDTO>>(){}.getType());

                if (dtoList != null) {
                    for (UnitJsonDTO dto : dtoList) {
                        // 2. DTO를 실제 도메인 모델(UnitInfo)로 변환
                        UnitInfo info = convertToUnitInfo(dto);
                        MingtdUnits.register(info);

                        if (dto.recipes != null) {
                            for (UnitRecipe jsonRecipe : dto.recipes) {
                                // 2. UnitRecipe는 레코드이므로 반드시 () 메서드로 호출해야 함
                                // 3. dto.id는 클래스 필드라면 dto.id, 레코드라면 dto.id()
                                UpgradeRecipe domainRecipe = new UpgradeRecipe(
                                        jsonRecipe.recipeId(),    // 레코드 메서드 호출
                                        jsonRecipe.ingredients(), // 레코드 메서드 호출
                                        dto.id                    // 클래스 필드 접근 (레코드라면 dto.id())
                                );

                                // 4. 역방향 인덱스 생성
                                for (String ingredientId : jsonRecipe.ingredients().keySet()) {
                                    MingtdUnits.RECIPES_BY_INGREDIENT
                                            .computeIfAbsent(ingredientId, k -> new ArrayList<>())
                                            .add(domainRecipe);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[MingTD] 유닛 데이터 파일 파싱 오류: " + id);
                e.printStackTrace();
            }
        });

        // --- 로그 출력 로직 추가 ---
        logLoadingResult();
//        System.out.println("[MingTD] 총 " + MingtdUnits.UNIT_REGISTRY.size() + "개의 유닛 정보를 로드했습니다.");
    }

    public UnitInfo convertToUnitInfo(UnitJsonDTO dto) {
        // String -> Enum 변환
        Rarity rarity = Rarity.valueOf(dto.rarity.toUpperCase());
        DefenseConfig.AttackType attackType = DefenseConfig.AttackType.valueOf(dto.attackType.toUpperCase());

        // 조합법 변환 (UnitRecipe 객체 생성)
        List<UnitRecipe> recipes = new ArrayList<>();
        if (dto.recipes != null) {
            for (UnitRecipe r : dto.recipes) {
                recipes.add(new UnitRecipe(r.recipeId(), r.ingredients()));
            }
        }

        // 스킬 변환 (현재는 빈 리스트로 처리하거나 SkillFactory 연결)
        // List<UnitSkill> skills = dto.skills.stream().map(...)
        List<UnitSkill> skills = new ArrayList<>();

        return new UnitInfo(
                dto.id,
                dto.name,
                rarity,
                dto.mainItem,
                dto.baseDamage,
                dto.attackSpeed,
                dto.attackRange,
                attackType,
                recipes,
                skills
        );
    }

    // JSON 구조와 매핑될 내부 헬퍼 클래스 (DTO)
    public static class UnitJsonDTO {
        String id;
        String name;
        String rarity;
        String mainItem;
        float baseDamage;
        float attackSpeed;
        float attackRange;
        String attackType;
        List<UnitRecipe> recipes;
        List<UnitSkill> skills;
    }

    private void logLoadingResult() {
        int total = MingtdUnits.UNIT_REGISTRY.size();
        System.out.println("========================================");
        System.out.println("[MingTD] 유닛 데이터 로딩 완료!");
        System.out.println("[MingTD] 총 유닛 수: " + total);

        // 등급별로 몇 개씩 로드되었는지 확인 (디버깅용)
        Map<Rarity, Long> counts = MingtdUnits.UNIT_REGISTRY.values().stream()
                .collect(Collectors.groupingBy(UnitInfo::rarity, Collectors.counting()));

        counts.forEach((rarity, count) -> {
            System.out.println(" > " + rarity.name() + ": " + count + "개");
        });

        // 로드된 유닛 ID 리스트 출력 (간략하게)
        String allIds = String.join(", ", MingtdUnits.UNIT_REGISTRY.keySet());
        System.out.println("[MingTD] 로드된 ID: [" + allIds + "]");
        System.out.println("========================================");
    }
}
