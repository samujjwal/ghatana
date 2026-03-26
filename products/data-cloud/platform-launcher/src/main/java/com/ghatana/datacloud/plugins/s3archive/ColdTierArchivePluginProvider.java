package com.ghatana.datacloud.plugins.s3archive;

import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginProvider;
import com.ghatana.platform.plugin.PluginType;
import org.jetbrains.annotations.NotNull;

/**
 * ServiceLoader provider for S3 cold tier archive plugin.
 * 
 * <p>This provider enables automatic discovery of the S3
 * archive plugin via Java's ServiceLoader mechanism.
 *
 * @doc.type class
 * @doc.purpose ServiceLoader provider for S3 archive plugin
 * @doc.layer plugin
 * @doc.pattern Provider, Factory
 */
public class ColdTierArchivePluginProvider implements PluginProvider {

    @Override
    public @NotNull Plugin createPlugin() {
        return new ColdTierArchivePlugin();
    }

    @Override
    public @NotNull PluginMetadata getMetadata() {
        return PluginMetadata.builder()
            .id("s3-l4-cold-archive")
            .name("S3 L4 Cold Archive Plugin")
            .version("1.0.0")
            .vendor("Ghatana")
            .description("S3 L4 (COLD tier) archive storage with Glacier transitions")
            .type(PluginType.STORAGE)
            .build();
    }

    @Override
    public int priority() {
        return 10; // Lower priority for archive tier
    }
}
