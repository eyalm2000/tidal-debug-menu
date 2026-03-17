package app.revanced.patches.tidal;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.Closeable;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class TidalDebugPatchCompat {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String DEBUG_ACTIVITY = "com.tidal.android.debugmenu.DebugMenuActivity";

    private TidalDebugPatchCompat() {
    }

    // Public static, non-parameterized method so patch loaders can discover it.
    public static Object unlockDebugMenuPatch() {
        try {
            return createPatch();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Tidal debug patch", e);
        }
    }

    // Public static, non-parameterized method so patch loaders can discover it.
    public static Object exportDebugActivityPatch() {
        try {
            return createExportActivityPatch();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create export debug activity patch", e);
        }
    }

    private static Object createPatch() throws Exception {
        Class<?> patchKtClass = Class.forName("app.revanced.patcher.patch.PatchKt");
        Method bytecodePatch = findPatchFactory(patchKtClass, "bytecodePatch");

        return createPatchWithApplyBlock(
            bytecodePatch,
            "Unlock Debug Menu",
            "Enables the internal debug menu in Tidal settings.",
            new ContextAction() {
                @Override
                public void run(Object context) throws Exception {
                    forceDebugMenuReturnTrue(context);
                }
            }
        );
    }

    private static Object createExportActivityPatch() throws Exception {
        Class<?> patchKtClass = Class.forName("app.revanced.patcher.patch.PatchKt");
        Method resourcePatch = findPatchFactory(patchKtClass, "resourcePatch");

        return createPatchWithApplyBlock(
            resourcePatch,
            "Export Debug Activity",
            "Ensures the Tidal debug activity is exported in AndroidManifest.xml.",
            new ContextAction() {
                @Override
                public void run(Object context) throws Exception {
                    ensureDebugActivityExported(context);
                }
            }
        );
    }

    private static Object createPatchWithApplyBlock(Method patchFactory, String name, String description, final ContextAction action) throws Exception {
        Function1<Object, Unit> builderBlock = new Function1<>() {
            @Override
            public Unit invoke(Object builder) {
                try {
                    attachApplyOrExecute(builder, action);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return Unit.INSTANCE;
            }
        };

        return patchFactory.invoke(null, name, description, true, builderBlock);
    }

    private static Method findPatchFactory(Class<?> patchKtClass, String factoryName) {
        for (Method method : patchKtClass.getDeclaredMethods()) {
            if (!method.getName().equals(factoryName)) continue;
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != 4) continue;
            if (paramTypes[0] != String.class || paramTypes[1] != String.class || paramTypes[2] != boolean.class) continue;
            if (!Function1.class.isAssignableFrom(paramTypes[3])) continue;

            method.setAccessible(true);
            return method;
        }

        throw new IllegalStateException("Could not locate PatchKt." + factoryName + "(String, String, boolean, Function1).");
    }

    private static void attachApplyOrExecute(Object builder, final ContextAction action) throws Exception {
        Function1<Object, Unit> applyBlock = new Function1<>() {
            @Override
            public Unit invoke(Object context) {
                try {
                    action.run(context);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return Unit.INSTANCE;
            }
        };

        Method applyOrExecute = null;
        for (Method method : builder.getClass().getMethods()) {
            if (method.getParameterCount() != 1) continue;
            if (!Function1.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
            if (method.getName().equals("apply") || method.getName().equals("execute")) {
                applyOrExecute = method;
                break;
            }
        }

        if (applyOrExecute == null) {
            throw new IllegalStateException("Could not locate apply/execute method on bytecode patch builder.");
        }

        applyOrExecute.invoke(builder, applyBlock);
    }

    private static void ensureDebugActivityExported(Object context) throws Exception {
        Method documentMethod = context.getClass().getMethod("document", String.class);
        Object documentObject = documentMethod.invoke(context, "AndroidManifest.xml");
        Document document = (Document) documentObject;

        NodeList activities = document.getElementsByTagName("activity");
        boolean found = false;

        for (int index = 0; index < activities.getLength(); index++) {
            Element activity = (Element) activities.item(index);
            String activityName = activity.getAttributeNS(ANDROID_NS, "name");
            if (activityName == null || activityName.isEmpty()) {
                activityName = activity.getAttribute("android:name");
            }
            if (!DEBUG_ACTIVITY.equals(activityName)) continue;

            activity.setAttributeNS(ANDROID_NS, "android:exported", "true");
            found = true;
            break;
        }

        if (documentObject instanceof Closeable closeable) {
            closeable.close();
        }

        if (!found) {
            throw new IllegalStateException("Could not locate " + DEBUG_ACTIVITY + " in AndroidManifest.xml.");
        }
    }

    private static void forceDebugMenuReturnTrue(Object context) throws Exception {
        Object classes = invokeNoArg(context, "getClasses");

        Object targetClass = null;
        for (Object classDef : (Iterable<?>) classes) {
            String type = (String) invokeNoArg(classDef, "getType");
            // Match the actual class, not generated inner classes like ...$isLoggingEnabledFlow$1.
            if (type.endsWith("/DebugFeatureInteractorDefault;") && !type.contains("$")) {
                targetClass = classDef;
                break;
            }
        }
        if (targetClass == null) {
            // Fallback for potential packaging variations while still avoiding inner classes.
            for (Object classDef : (Iterable<?>) classes) {
                String type = (String) invokeNoArg(classDef, "getType");
                if (type.contains("DebugFeatureInteractorDefault") && !type.contains("$")) {
                    targetClass = classDef;
                    break;
                }
            }
        }
        if (targetClass == null) {
            throw new IllegalStateException("Could not locate DebugFeatureInteractorDefault class.");
        }

        Method proxyMethod = null;
        for (Method method : context.getClass().getMethods()) {
            if (method.getName().equals("proxy") && method.getParameterCount() == 1) {
                proxyMethod = method;
                break;
            }
        }
        if (proxyMethod == null) {
            throw new IllegalStateException("Could not locate proxy(ClassDef) method.");
        }

        Object proxy = proxyMethod.invoke(context, targetClass);
        Object mutableClass = invokeNoArg(proxy, "getMutableClass");
        Iterable<?> methods = (Iterable<?>) invokeNoArg(mutableClass, "getMethods");

        Object targetMethod = null;
        Object fallbackNoArgBooleanMethod = null;
        for (Object method : methods) {
            String name = (String) invokeNoArg(method, "getName");
            String returnType = (String) invokeNoArg(method, "getReturnType");
            java.util.List<?> parameterTypes = (java.util.List<?>) invokeNoArg(method, "getParameterTypes");
            int paramCount = parameterTypes.size();

            if ("Z".equals(returnType) && paramCount == 0 && fallbackNoArgBooleanMethod == null) {
                fallbackNoArgBooleanMethod = method;
            }

            // Support both known signatures:
            // - a()Z
            // - a(Ljava/lang/String;)Z
            if (!"a".equals(name) || !"Z".equals(returnType)) continue;
            if (paramCount == 0) {
                targetMethod = method;
                break;
            }
            if (paramCount == 1 && "Ljava/lang/String;".equals(parameterTypes.get(0))) {
                targetMethod = method;
                break;
            }
        }
        if (targetMethod == null) {
            targetMethod = fallbackNoArgBooleanMethod;
        }
        if (targetMethod == null) {
            throw new IllegalStateException("Could not locate a suitable boolean gate method in DebugFeatureInteractorDefault.");
        }

        Class<?> instructionExtensionsClass = Class.forName("app.revanced.patcher.extensions.InstructionExtensions");
        Object instructionExtensions = instructionExtensionsClass.getField("INSTANCE").get(null);

        Method addInstructions = null;
        for (Method method : instructionExtensionsClass.getMethods()) {
            if (!method.getName().equals("addInstructions")) continue;
            if (method.getParameterCount() != 3) continue;
            if (!Modifier.isPublic(method.getModifiers())) continue;
            addInstructions = method;
            break;
        }
        if (addInstructions == null) {
            throw new IllegalStateException("Could not locate InstructionExtensions.addInstructions.");
        }

        addInstructions.invoke(
            instructionExtensions,
            targetMethod,
            0,
            "const/4 v0, 0x1\nreturn v0"
        );
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private interface ContextAction {
        void run(Object context) throws Exception;
    }
}
