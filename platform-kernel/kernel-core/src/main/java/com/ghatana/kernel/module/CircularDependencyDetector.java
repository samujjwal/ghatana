package com.ghatana.kernel.module;

import com.ghatana.kernel.descriptor.KernelDependency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects circular dependencies across kernel modules.
 *
 * @doc.type class
 * @doc.purpose Detect cycles in module dependency graph
 * @doc.layer core
 * @doc.pattern Validator
 */
public final class CircularDependencyDetector {

    /**
     * Detects cycles in the provided module set.
     *
     * @param modules modules to analyze
     * @return list of module IDs involved in cycles, empty when acyclic
     */
    public List<String> detect(Set<KernelModule> modules) {
        Map<String, KernelModule> moduleById = new HashMap<>();
        for (KernelModule module : modules) {
            moduleById.put(module.getModuleId(), module);
        }

        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
        List<String> cycleNodes = new ArrayList<>();

        for (String moduleId : moduleById.keySet()) {
            if (!visited.contains(moduleId) && dfs(moduleId, moduleById, visited, stack, cycleNodes)) {
                if (!cycleNodes.contains(moduleId)) {
                    cycleNodes.add(moduleId);
                }
            }
        }

        return cycleNodes;
    }

    private boolean dfs(String moduleId, Map<String, KernelModule> moduleById,
            Set<String> visited, Set<String> stack, List<String> cycleNodes) {
        visited.add(moduleId);
        stack.add(moduleId);

        KernelModule module = moduleById.get(moduleId);
        if (module != null) {
            for (KernelDependency dependency : module.getDependencies()) {
                if (!dependency.isModuleDependency()) {
                    continue;
                }
                String depId = dependency.getDependencyId();
                if (!moduleById.containsKey(depId)) {
                    continue;
                }
                if (!visited.contains(depId)) {
                    if (dfs(depId, moduleById, visited, stack, cycleNodes)) {
                        cycleNodes.add(moduleId);
                        return true;
                    }
                } else if (stack.contains(depId)) {
                    cycleNodes.add(depId);
                    cycleNodes.add(moduleId);
                    return true;
                }
            }
        }

        stack.remove(moduleId);
        return false;
    }
}
