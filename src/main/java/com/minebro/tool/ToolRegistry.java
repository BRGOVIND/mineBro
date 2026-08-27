package com.minebro.tool;

import com.minebro.provider.model.ToolSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {

    private final Map<String, MineBroTool> tools = new LinkedHashMap<>();

    public void register(MineBroTool tool) {
        tools.put(tool.id(), tool);
    }

    public Optional<MineBroTool> find(String id) {
        return Optional.ofNullable(tools.get(id));
    }

    public List<MineBroTool> allAtOrBelow(PermissionLevel level) {
        List<MineBroTool> result = new ArrayList<>();
        for (MineBroTool tool : tools.values()) {
            if (level.allows(tool.requiredPermission())) {
                result.add(tool);
            }
        }
        return result;
    }

    public List<ToolSchema> schemasAtOrBelow(PermissionLevel level) {
        return allAtOrBelow(level).stream().map(MineBroTool::schema).toList();
    }
}
