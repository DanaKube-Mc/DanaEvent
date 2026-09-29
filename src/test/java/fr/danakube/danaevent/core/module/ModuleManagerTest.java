package fr.danakube.danaevent.core.module;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleManagerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private ModuleManager moduleManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        moduleManager = new ModuleManager(plugin);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    static class TestSimpleModule extends AbstractDanaModule {
        private final AtomicBoolean enabledFlag = new AtomicBoolean(false);
        private final AtomicBoolean disabledFlag = new AtomicBoolean(false);
        private final AtomicBoolean reloadedFlag = new AtomicBoolean(false);

        public TestSimpleModule(String id, String name, String version) {
            super(id, name, version);
        }

        @Override
        public void onEnable() {
            super.onEnable();
            enabledFlag.set(true);
        }

        @Override
        public void onDisable() {
            super.onDisable();
            disabledFlag.set(true);
        }

        @Override
        public void onReload() {
            super.onReload();
            reloadedFlag.set(true);
        }
    }

    static class FaultyEnableModule extends AbstractDanaModule {
        private final AtomicBoolean disableAttempted = new AtomicBoolean(false);

        public FaultyEnableModule(String id) {
            super(id, "Faulty Enable Module", "1.0.0");
        }

        @Override
        public void onEnable() {
            throw new RuntimeException("Simulated catastrophic crash in onEnable()");
        }

        @Override
        public void onDisable() {
            super.onDisable();
            disableAttempted.set(true);
        }
    }

    static class FaultyReloadModule extends AbstractDanaModule {
        public FaultyReloadModule(String id) {
            super(id, "Faulty Reload Module", "1.0.0");
        }

        @Override
        public void onReload() {
            throw new RuntimeException("Simulated crash in onReload()");
        }
    }

    @Nested
    @DisplayName("Registration & Retrieval")
    class RegistrationTests {

        @Test
        @DisplayName("Should successfully register and retrieve a module by id case-insensitively")
        void shouldRegisterAndRetrieveModule() {
            TestSimpleModule module = new TestSimpleModule("quiz", "Quiz Module", "1.0.0");
            moduleManager.registerModule(module);

            assertThat(moduleManager.getModule("quiz")).isPresent().contains(module);
            assertThat(moduleManager.getModule("QUIZ")).isPresent().contains(module);
            assertThat(moduleManager.getModules()).contains(module);
            assertThat(moduleManager.getModuleStatus("quiz")).isEqualTo(ModuleStatus.DISABLED);
        }

        @Test
        @DisplayName("Should reject null module registration or duplicate id")
        void shouldRejectNullOrDuplicate() {
            assertThatThrownBy(() -> moduleManager.registerModule(null))
                .isInstanceOf(NullPointerException.class);

            TestSimpleModule mod1 = new TestSimpleModule("rush", "Rush", "1.0");
            moduleManager.registerModule(mod1);

            TestSimpleModule mod2 = new TestSimpleModule("RUSH", "Rush 2", "2.0");
            assertThatThrownBy(() -> moduleManager.registerModule(mod2))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should return empty optional for non-existing or null module id")
        void shouldHandleMissingModuleSafely() {
            assertThat(moduleManager.getModule("nonexistent")).isEmpty();
            assertThat(moduleManager.getModule(null)).isEmpty();
            assertThat(moduleManager.getModuleStatus("nonexistent")).isEqualTo(ModuleStatus.DISABLED);
            assertThat(moduleManager.getModuleStatus(null)).isEqualTo(ModuleStatus.DISABLED);
        }
    }

    @Nested
    @DisplayName("Lifecycle & Global Operations")
    class LifecycleTests {

        @Test
        @DisplayName("Should enable, disable and reload a module normally")
        void shouldEnableDisableReloadModule() {
            TestSimpleModule module = new TestSimpleModule("pvp", "PvP Module", "1.0.0");
            moduleManager.registerModule(module);

            boolean enabled = moduleManager.enableModule("pvp");
            assertThat(enabled).isTrue();
            assertThat(module.isEnabled()).isTrue();
            assertThat(module.enabledFlag.get()).isTrue();
            assertThat(moduleManager.getModuleStatus("pvp")).isEqualTo(ModuleStatus.ENABLED);

            boolean reloaded = moduleManager.reloadModule("pvp");
            assertThat(reloaded).isTrue();
            assertThat(module.reloadedFlag.get()).isTrue();
            assertThat(module.isEnabled()).isTrue();
            assertThat(moduleManager.getModuleStatus("pvp")).isEqualTo(ModuleStatus.ENABLED);

            boolean disabled = moduleManager.disableModule("pvp");
            assertThat(disabled).isTrue();
            assertThat(module.isEnabled()).isFalse();
            assertThat(module.disabledFlag.get()).isTrue();
            assertThat(moduleManager.getModuleStatus("pvp")).isEqualTo(ModuleStatus.DISABLED);
        }

        @Test
        @DisplayName("enableAll, disableAll and reloadAll should apply to all registered modules")
        void shouldManageAllModulesGlobally() {
            TestSimpleModule modA = new TestSimpleModule("modA", "Module A", "1.0");
            TestSimpleModule modB = new TestSimpleModule("modB", "Module B", "1.0");
            moduleManager.registerModule(modA);
            moduleManager.registerModule(modB);

            moduleManager.enableAll();
            assertThat(modA.isEnabled()).isTrue();
            assertThat(modB.isEnabled()).isTrue();
            assertThat(moduleManager.getModuleStatus("modA")).isEqualTo(ModuleStatus.ENABLED);
            assertThat(moduleManager.getModuleStatus("modB")).isEqualTo(ModuleStatus.ENABLED);

            moduleManager.reloadAll();
            assertThat(modA.reloadedFlag.get()).isTrue();
            assertThat(modB.reloadedFlag.get()).isTrue();

            moduleManager.disableAll();
            assertThat(modA.isEnabled()).isFalse();
            assertThat(modB.isEnabled()).isFalse();
            assertThat(moduleManager.getModuleStatus("modA")).isEqualTo(ModuleStatus.DISABLED);
            assertThat(moduleManager.getModuleStatus("modB")).isEqualTo(ModuleStatus.DISABLED);
        }
    }

    @Nested
    @DisplayName("Fault Isolation (Isolation des Pannes)")
    class FaultIsolationTests {

        @Test
        @DisplayName("When module throws in onEnable, ModuleManager should catch, log, attempt disable, set ERROR, and not crash")
        void shouldIsolateModuleOnEnableCrash() {
            FaultyEnableModule faulty = new FaultyEnableModule("crashmod");
            moduleManager.registerModule(faulty);

            boolean result = moduleManager.enableModule("crashmod");

            assertThat(result).isFalse();
            assertThat(faulty.isEnabled()).isFalse();
            assertThat(faulty.disableAttempted.get()).isTrue();
            assertThat(moduleManager.getModuleStatus("crashmod")).isEqualTo(ModuleStatus.ERROR);
        }

        @Test
        @DisplayName("enableAll should continue activating other modules even if one module crashes during onEnable")
        void shouldContinueEnablingOtherModulesWhenOneCrashes() {
            TestSimpleModule goodBefore = new TestSimpleModule("good1", "Good 1", "1.0");
            FaultyEnableModule faulty = new FaultyEnableModule("bad");
            TestSimpleModule goodAfter = new TestSimpleModule("good2", "Good 2", "1.0");

            moduleManager.registerModule(goodBefore);
            moduleManager.registerModule(faulty);
            moduleManager.registerModule(goodAfter);

            moduleManager.enableAll();

            assertThat(goodBefore.isEnabled()).isTrue();
            assertThat(moduleManager.getModuleStatus("good1")).isEqualTo(ModuleStatus.ENABLED);

            assertThat(faulty.isEnabled()).isFalse();
            assertThat(moduleManager.getModuleStatus("bad")).isEqualTo(ModuleStatus.ERROR);

            assertThat(goodAfter.isEnabled()).isTrue();
            assertThat(moduleManager.getModuleStatus("good2")).isEqualTo(ModuleStatus.ENABLED);
        }

        @Test
        @DisplayName("When module throws in onReload, it should be isolated and marked as ERROR")
        void shouldIsolateModuleOnReloadCrash() {
            FaultyReloadModule faulty = new FaultyReloadModule("crashreload");
            moduleManager.registerModule(faulty);
            moduleManager.enableModule("crashreload");

            boolean result = moduleManager.reloadModule("crashreload");

            assertThat(result).isFalse();
            assertThat(moduleManager.getModuleStatus("crashreload")).isEqualTo(ModuleStatus.ERROR);
        }
    }
}
