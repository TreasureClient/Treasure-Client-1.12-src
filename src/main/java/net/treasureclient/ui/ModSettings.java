package net.treasureclient.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.treasureclient.compat.V;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Opens other mods' own settings pages from Treasure Client's Mods screen, so a pack doesn't need
 * Mod Menu. Mods hand their settings page to Mod Menu through its small public API (the "modmenu"
 * entrypoint and the interfaces in com.terraformersmc.modmenu.api, MIT licensed, by
 * TerraformersMC). When Mod Menu is installed we just use its API. When it isn't, those interfaces
 * don't exist, so we write stand-ins with the same names and shapes (no Mod Menu code, only empty
 * interfaces) into a small jar and add it to the game's class path before asking the mods.
 * Mods that keep their settings inside Sodium's Video Settings (Sodium, Sodium Extra, Reese's
 * Sodium Options, Better Block Entities...) open Video Settings.
 */
public final class ModSettings {
    private ModSettings() {}

    private static final String API = "com/terraformersmc/modmenu/api/";
    private static Map<String, Object> factories;
    private static Method create;
    private static final java.util.Set<String> VIDEO = new java.util.HashSet<>();

    /** True if this mod has a settings page we can open. */
    public static boolean has(String id) {
        load();
        return factories.containsKey(id) || VIDEO.contains(id);
    }

    /** Opens the mod's settings page; false if it has none or it failed to open. */
    public static boolean open(String id, Screen parent) {
        load();
        Minecraft mc = Minecraft.getInstance();
        Object f = factories.get(id);
        if (f != null && create != null) {
            try {
                Object s = create.invoke(f, parent);
                if (s instanceof Screen screen) {
                    V.open(screen);
                    return true;
                }
            } catch (Throwable t) {
                Throwable c = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
                org.slf4j.LoggerFactory.getLogger("treasureclient").warn("Couldn't open the settings of {}", id, c);
            }
        }
        if (VIDEO.contains(id)) {
            V.open(new VideoSettingsScreen(parent, mc, mc.options));
            return true;
        }
        return false;
    }

    private static synchronized void load() {
        if (factories != null) return;
        factories = new HashMap<>();
        FabricLoader fl = FabricLoader.getInstance();
        // mods whose settings live in Sodium's Video Settings
        if (fl.isModLoaded("sodium")) {
            VIDEO.add("sodium");
            for (EntrypointContainer<Object> c : safe(() -> fl.getEntrypointContainers("sodium:config_api_user", Object.class)))
                VIDEO.add(top(c.getProvider()));
        }
        try {
            if (!fl.isModLoaded("modmenu")) addStandIns();
            ClassLoader cl = ModSettings.class.getClassLoader();
            Class<?> apiC = Class.forName(API.replace('/', '.') + "ModMenuApi", false, cl);
            Class<?> facC = Class.forName(API.replace('/', '.') + "ConfigScreenFactory", false, cl);
            Method own = apiC.getMethod("getModConfigScreenFactory"), provided = apiC.getMethod("getProvidedConfigScreenFactories");
            create = facC.getMethod("create", Screen.class);
            Map<String, Object> extra = new HashMap<>();
            for (EntrypointContainer<Object> c : safe(() -> fl.getEntrypointContainers("modmenu", Object.class))) {
                String id = c.getProvider().getMetadata().getId();
                try {
                    Object api = c.getEntrypoint();
                    if (!apiC.isInstance(api)) continue;
                    Object f = own.invoke(api);
                    if (f != null) {
                        factories.put(id, f);
                        factories.putIfAbsent(top(c.getProvider()), f);  // a mod nested inside another's jar
                    }
                    Object m = provided.invoke(api);
                    if (m instanceof Map<?, ?> map)
                        for (Map.Entry<?, ?> e : map.entrySet())
                            if (e.getKey() instanceof String k && e.getValue() != null) extra.putIfAbsent(k, e.getValue());
                } catch (Throwable t) {
                    org.slf4j.LoggerFactory.getLogger("treasureclient").warn("{} has a broken settings page", id, t);
                }
            }
            // like Mod Menu: a mod's own page wins over one another mod offers for it
            for (Map.Entry<String, Object> e : extra.entrySet()) factories.putIfAbsent(e.getKey(), e.getValue());
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("treasureclient").warn("Couldn't read the mods' settings pages", t);
        }
        factories.remove("treasureclient");  // ours opens Treasure Client's own menu
    }

    private static String top(ModContainer c) {
        while (c.getContainingMod().isPresent()) c = c.getContainingMod().get();
        return c.getMetadata().getId();
    }

    private interface Get<T> { T get() throws Throwable; }

    private static <T> java.util.List<T> safe(Get<java.util.List<T>> g) {
        try {
            return g.get();
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("treasureclient").warn("Couldn't read a mod list", t);
            return java.util.List.of();
        }
    }

    // ------------------------------------------------------------------ stand-in API (when Mod Menu isn't installed)

    /** For mods that call Mod Menu's ModMenuApi.createModsScreen: our Mods screen. */
    public static Screen modsScreen(Screen parent) {
        return new ModListScreen(parent);
    }

    /** For mods that call Mod Menu's ModMenuApi.createModsButtonText. */
    public static Component modsButtonText() {
        return Component.literal("Mods");
    }

    private static void addStandIns() throws Exception {
        String screen = Type.getDescriptor(Screen.class), comp = Type.getDescriptor(Component.class);
        String me = Type.getInternalName(ModSettings.class);
        Map<String, byte[]> classes = new HashMap<>();

        ClassWriter w = iface("ConfigScreenFactory", "<S:" + screen + ">Ljava/lang/Object;");
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "create", "(" + screen + ")" + screen, "(" + screen + ")TS;", null).visitEnd();
        classes.put("ConfigScreenFactory", done(w));

        w = iface("UpdateChecker", null);
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "checkForUpdates", "()L" + API + "UpdateInfo;", null, null).visitEnd();
        classes.put("UpdateChecker", done(w));

        w = iface("UpdateInfo", null);
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "isUpdateAvailable", "()Z", null, null).visitEnd();
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getDownloadLink", "()Ljava/lang/String;", null, null).visitEnd();
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getUpdateChannel", "()L" + API + "UpdateChannel;", null, null).visitEnd();
        ret(w, Opcodes.ACC_PUBLIC, "getUpdateMessage", "()" + comp, null);
        classes.put("UpdateInfo", done(w));

        classes.put("UpdateChannel", channelEnum());

        w = iface("ModMenuApi", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "createModsScreen", "(" + screen + ")" + screen, null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, me, "modsScreen", "(" + screen + ")" + screen, false);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(1, 1);
        m.visitEnd();
        m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "createModsButtonText", "()" + comp, null, null);
        m.visitCode();
        m.visitMethodInsn(Opcodes.INVOKESTATIC, me, "modsButtonText", "()" + comp, false);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(1, 0);
        m.visitEnd();
        ret(w, Opcodes.ACC_PUBLIC, "getModConfigScreenFactory", "()L" + API + "ConfigScreenFactory;", null);
        ret(w, Opcodes.ACC_PUBLIC, "getUpdateChecker", "()L" + API + "UpdateChecker;", null);
        ret(w, Opcodes.ACC_PUBLIC, "getProvidedConfigScreenFactories", "()Ljava/util/Map;", "map");
        ret(w, Opcodes.ACC_PUBLIC, "getProvidedUpdateCheckers", "()Ljava/util/Map;", "map");
        m = w.visitMethod(Opcodes.ACC_PUBLIC, "attachModpackBadges", "(Ljava/util/function/Consumer;)V", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 2);
        m.visitEnd();
        classes.put("ModMenuApi", done(w));

        Path jar = Files.createTempFile("treasureclient-modmenu-api", ".jar");
        jar.toFile().deleteOnExit();
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                out.putNextEntry(new JarEntry(API + e.getKey() + ".class"));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        net.fabricmc.loader.impl.launch.FabricLauncherBase.getLauncher().addToClassPath(jar);
    }

    private static ClassWriter iface(String name, String signature) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, API + name, signature,
            "java/lang/Object", null);
        return w;
    }

    private static byte[] done(ClassWriter w) {
        w.visitEnd();
        return w.toByteArray();
    }

    /** A default method that returns null, or an empty Map.of() when what is "map". */
    private static void ret(ClassWriter w, int access, String name, String desc, String what) {
        MethodVisitor m = w.visitMethod(access, name, desc, null, null);
        m.visitCode();
        if ("map".equals(what)) m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Map", "of", "()Ljava/util/Map;", true);
        else m.visitInsn(Opcodes.ACONST_NULL);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(1, 1);
        m.visitEnd();
    }

    /** enum UpdateChannel { ALPHA, BETA, RELEASE; static UpdateChannel getUserPreference() { return RELEASE; } } */
    private static byte[] channelEnum() {
        String n = API + "UpdateChannel", d = "L" + n + ";";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, n,
            "Ljava/lang/Enum<" + d + ">;", "java/lang/Enum", null);
        String[] names = {"ALPHA", "BETA", "RELEASE"};
        for (String s : names)
            w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, s, d, null, null).visitEnd();
        w.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", "[" + d, null, null).visitEnd();

        MethodVisitor m = w.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitVarInsn(Opcodes.ILOAD, 2);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(3, 3);
        m.visitEnd();

        m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        for (int i = 0; i < names.length; i++) {
            m.visitTypeInsn(Opcodes.NEW, n);
            m.visitInsn(Opcodes.DUP);
            m.visitLdcInsn(names[i]);
            m.visitIntInsn(Opcodes.BIPUSH, i);
            m.visitMethodInsn(Opcodes.INVOKESPECIAL, n, "<init>", "(Ljava/lang/String;I)V", false);
            m.visitFieldInsn(Opcodes.PUTSTATIC, n, names[i], d);
        }
        m.visitIntInsn(Opcodes.BIPUSH, names.length);
        m.visitTypeInsn(Opcodes.ANEWARRAY, n);
        for (int i = 0; i < names.length; i++) {
            m.visitInsn(Opcodes.DUP);
            m.visitIntInsn(Opcodes.BIPUSH, i);
            m.visitFieldInsn(Opcodes.GETSTATIC, n, names[i], d);
            m.visitInsn(Opcodes.AASTORE);
        }
        m.visitFieldInsn(Opcodes.PUTSTATIC, n, "$VALUES", "[" + d);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(6, 0);
        m.visitEnd();

        m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()[" + d, null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, n, "$VALUES", "[" + d);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "[" + d, "clone", "()Ljava/lang/Object;", false);
        m.visitTypeInsn(Opcodes.CHECKCAST, "[" + d);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(1, 0);
        m.visitEnd();

        m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "valueOf", "(Ljava/lang/String;)" + d, null, null);
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType(n));
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
        m.visitTypeInsn(Opcodes.CHECKCAST, n);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(2, 1);
        m.visitEnd();

        m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getUserPreference", "()" + d, null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, n, "RELEASE", d);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(1, 0);
        m.visitEnd();
        return done(w);
    }
}
