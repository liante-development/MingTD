package com.liante.recipe;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpgradeRecipeLoader implements SynchronousResourceReloader {
    private static final Gson GSON = new Gson();
    public static Map<String, UpgradeRecipe> RECIPES = new HashMap<>();

    @Override
    public void reload(ResourceManager manager) {
        Map<String, UpgradeRecipe> loaded = new HashMap<>();
        var resources = manager.findResources("upgrades", path -> path.getPath().endsWith(".json"));

        resources.forEach((id, resource) -> {
            try (InputStreamReader reader = new InputStreamReader(resource.getInputStream())) {
                // [변경점] 단일 객체가 아닌 List<UpgradeRecipe>로 파싱
                List<UpgradeRecipe> recipeList = GSON.fromJson(reader, new TypeToken<List<UpgradeRecipe>>(){}.getType());

                if (recipeList != null) {
                    for (UpgradeRecipe recipe : recipeList) {
                        // baseId를 키로 사용하여 맵에 저장
                        loaded.put(recipe.baseId(), recipe);
                    }
                }
            } catch (Exception e) {
                System.err.println("[MingTD] 레시피 파일 파싱 오류: " + id);
                e.printStackTrace();
            }
        });

        RECIPES = loaded;
        System.out.println("[MingTD] 총 " + RECIPES.size() + "개의 승급 조합법을 로드했습니다.");
    }
}