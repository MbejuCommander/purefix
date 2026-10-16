package dev.purefix.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.Arrays;

/** The same configurable alias is parsed, completed, and executed by Brigadier. */
public final class DonkeyCommands {
    private DonkeyCommands() {}
    public static <S> void register(CommandDispatcher<S> dispatcher, String pattern,
            SuggestionProvider<S> names, Consumer<String> start, Runnable usage, Predicate<String> available) {
        Rules.validateTrigger(pattern, true);
        String normalized = Rules.command(pattern);
        String[] parts = normalized.split(" ");
        var player = RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.word())
            .requires(source -> available.test(normalized)).suggests(names).executes(context -> { start.accept(StringArgumentType.getString(context, "player")); return 1; });
        String parent = String.join(" ", Arrays.copyOf(parts, parts.length - 1));
        var branch = LiteralArgumentBuilder.<S>literal(parts[parts.length - 2]).requires(source -> available.test(parent))
            .executes(context -> { usage.run(); return 1; }).then(player);
        for (int i = parts.length - 3; i >= 0; i--) {
            String prefix = String.join(" ", Arrays.copyOf(parts, i + 1));
            branch = LiteralArgumentBuilder.<S>literal(parts[i]).requires(source -> available.test(prefix))
                .executes(context -> { usage.run(); return 1; }).then(branch);
        }
        dispatcher.register(branch);
    }
}
