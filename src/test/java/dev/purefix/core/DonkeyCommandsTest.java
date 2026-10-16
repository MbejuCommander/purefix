package dev.purefix.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class DonkeyCommandsTest {
    private final List<String> players = List.of("Alex", "Steve", "Never_one", "_nullion");
    private final AtomicReference<String> alias = new AtomicReference<>("donkey {player}");
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final List<String> targets = new ArrayList<>();
    private final AtomicInteger usages = new AtomicInteger();
    private final CommandDispatcher<List<String>> dispatcher = new CommandDispatcher<>();
    private void register() {
        DonkeyCommands.register(dispatcher, alias.get(), (context, builder) -> {
            context.getSource().stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                .forEach(builder::suggest);
            return builder.buildFuture();
        }, targets::add, usages::incrementAndGet, prefix -> enabled.get()
            && (alias.get().equals(prefix) || alias.get().startsWith(prefix + " ")));
    }
    private List<String> suggestions(String input) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, players)).join().getList().stream().map(s -> s.getText()).toList();
    }
    @Test void playerNamesCompleteAndFilterByTypedPrefix() {
        register(); assertEquals(new TreeSet<>(players), new TreeSet<>(suggestions("donkey ")));
        assertEquals(List.of("Never_one"), suggestions("donkey Ne"));
        assertEquals(List.of("donkey"), suggestions("don"));
    }
    @Test void bareAliasShowsUsageWithoutStartingInventoryCapture() throws Exception {
        register(); dispatcher.execute("donkey", players);
        assertEquals(1, usages.get()); assertTrue(targets.isEmpty());
    }
    @Test void selectedOrManuallyTypedNamesStartExactlyOnce() throws Exception {
        register(); dispatcher.execute("donkey Never_one", players); dispatcher.execute("donkey Offline_Player", players);
        assertEquals(List.of("Never_one", "Offline_Player"), targets);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("donkey Steve extra", players));
    }
    @Test void changingAliasAndNestedPrefixesDisablesOldPaths() throws Exception {
        register(); alias.set("donkey inspect {player}"); register();
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("donkey Steve", players));
        assertEquals(List.of("Steve"), suggestions("donkey inspect St"));
        dispatcher.execute("donkey inspect Steve", players);
        alias.set("loot {player}"); register();
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("donkey inspect Steve", players));
        dispatcher.execute("loot Alex", players); assertEquals(List.of("Steve", "Alex"), targets);
    }
    @Test void disablingAliasLeavesNoExecutableClientCommand() throws Exception {
        register(); enabled.set(false);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("donkey Steve", players));
        assertTrue(targets.isEmpty()); enabled.set(true); dispatcher.execute("donkey Steve", players);
        assertEquals(List.of("Steve"), targets);
    }
}
