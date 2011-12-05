package net.ocheyedan.ply.props;

import net.ocheyedan.ply.FileUtil;
import net.ocheyedan.ply.Output;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: blangel
 * Date: 10/21/11
 * Time: 12:24 PM
 *
 * Extensions to {@link Props} which should only be visible/applicable for the ply application itself (not any dependent
 * scripts).
 */
public final class PropsExt {

    /**
     * A cache of project-path/scope to resolved environment properties.
     */
    private static final Map<String, Map<String, String>> RESOLVED_ENV_CACHE = new HashMap<String, Map<String, String>>();

    /**
     * If the scope is not the default and the property is not found the default-scope will be consulted
     * @param projectConfigDir the project configuration directory from which to resolve the property
     * @param context to find {@code propertyName}
     * @param scope to find {@code propertyName}
     * @param propertyName of the property to retrieve
     * @return the property for {@code context} and {@code scope} named {@code propertyName} or null if
     *         no such property exists
     */
    public static Prop get(File projectConfigDir, String context, String scope, String propertyName) {
        Map<String, Map<String, Prop>> props = Loader.loadProjectProps(projectConfigDir, scope);
        Map<String, Prop> contextProps = (props == null ? null : props.get(context));
        if (contextProps == null) {
            return null;
        }
        return contextProps.get(propertyName);
    }

    /**
     * If the scope is not the default and the property is not found the default-scope will be consulted
     * @param projectConfigDir the project configuration directory from which to resolve the property
     * @param context to find {@code propertyName}
     * @param scope to find {@code propertyName}
     * @param propertyName of the property to retrieve
     * @return the property value for {@code context} and {@code scope} named {@code propertyName} or empty string if
     *         no such property exists
     */
    public static String getValue(File projectConfigDir, String context, String scope, String propertyName) {
        Prop prop = get(projectConfigDir, context, scope, propertyName);
        return (prop == null ? "" : prop.value);
    }

    /**
     * From the resolved properties, creates a mapping appropriate for exporting to a process's system environment
     * variables.  The mapping returned by this method will only include the contexts' {@code scope} (and the default scope's
     * if the given {@code scope} didn't override the default scope's property).
     * The key is composed of 'ply$' (to distinguish ply variables from other system environment variables) concatenated with
     * the context concatenated with '.' and the property name (note, the scope has been discarded).
     * @param projectPlyDir of the project for which to resolve environment properties.
     * @param projectConfigDir configuration directory associated with {@code projectDir} (typically based at {@code projectDir}
     *                         and named {@literal .ply/config}).
     * @param scope of the properties to include in the environment properties mapping
     * @return a mapping of env-property-name to property value (using {@code scope})
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getPropsForEnv(File projectPlyDir, File projectConfigDir, String scope) {
        String projectPlyDirCanonicalPath, projectConfigCanonicalDirPath;
        try {
            projectConfigCanonicalDirPath = projectConfigDir.getCanonicalPath();
            projectPlyDirCanonicalPath = projectPlyDir.getCanonicalPath();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        String cacheKey = FileUtil.pathFromParts(projectConfigCanonicalDirPath, scope);
        if (RESOLVED_ENV_CACHE.containsKey(cacheKey)) {
            return RESOLVED_ENV_CACHE.get(cacheKey);
        }
        Map<String, Map<String, Prop>> scopedProps = Loader.loadProjectProps(projectConfigDir, scope);
        Map<String, String> envProps = new HashMap<String, String>(scopedProps.size() * 5); // assume avg of 5 props per context?
        for (String context : scopedProps.keySet()) {
            Map<String, Prop> contextProps = scopedProps.get(context);
            for (String propertyName : contextProps.keySet()) {
                String envKey = "ply$" + context + "." + propertyName;
                Prop prop = contextProps.get(propertyName);
                Prop userScopedProp = new Prop(context, scope, propertyName, prop.value, false);
                envProps.put(envKey, Props.filterForPly(projectConfigCanonicalDirPath, userScopedProp, scopedProps));
            }
        }
        // now add some synthetic properties like the local ply directory location.
        envProps.put("ply$ply.project.dir", projectPlyDirCanonicalPath);
        envProps.put("ply$ply.java", System.getProperty("ply.java"));
        // scripts are always executed from the '.ply/../' directory, allow them to know where the 'ply' invocation
        // actually occurred.
        envProps.put("ply$ply.original.user.dir", System.getProperty("user.dir"));
        // allow scripts access to which scope in which they are being invoked.
        envProps.put("ply$ply.scope", (scope == null ? "" : scope));

        RESOLVED_ENV_CACHE.put(cacheKey, envProps);
        return envProps;
    }

    /**
     * If the scope is the default returns {@link Props#getProps(String)} for the supplied {@code context}, otherwise
     * the default scope is augmented with the properties particular to {@code scope}.
     * @param projectConfigDir the project configuration directory from which to resolve the property
     * @param context for which to get properties
     * @param scope for which to get properties
     * @return the property for {@code context} and {@code scope} or null if
     *         no such properties exists
     */
    public static Map<String, Prop> getPropsForScope(File projectConfigDir, String context, String scope) {
        Map<String, Map<String, Prop>> props = Loader.loadProjectProps(projectConfigDir, scope);
        Map<String, Prop> contextProps = new HashMap<String, Prop>();
        if ((props != null) && (props.get(context) != null)) {
            contextProps.putAll(props.get(context));
        }
        return contextProps;
    }

    /**
     * @param projectConfigDir the project configuration directory from which to resolve the properties to use for filtering
     * @param prop whose value will be filtered
     * @param scope from which to look for property resolution while filtering
     * @return the filtered value of {@code prop}
     */
    public static String filterForPly(File projectConfigDir, Prop prop, String scope) {
        return Props.filterForPly(projectConfigDir, prop, scope);
    }

    /**
     * Parses {@code propAtom} according to the format {@literal context[#scope].propertyName=propertyValue}
     * @param propAtom to parse
     * @return the parsed {@link Prop} or null of {@code propAtom} is not of the format.
     */
    public static Prop parse(String propAtom) {
        if (propAtom == null) {
            return null;
        }
        String context = "", scope = "", propertyName = "", propertyValue = "";
        if (propAtom.contains("#")) {
            int scopeIndex = propAtom.indexOf("#");
            context = propAtom.substring(0, scopeIndex);
            propAtom = propAtom.substring(scopeIndex + 1);
            int propIndex = propAtom.indexOf(".");
            if (propIndex == -1) {
                return null;
            }
            scope = propAtom.substring(0, propIndex);
            propAtom = propAtom.substring(propIndex + 1);
        } else if (propAtom.contains(".")) {
            int propIndex = propAtom.indexOf(".");
            context = propAtom.substring(0, propIndex);
            propAtom = propAtom.substring(propIndex + 1);
        } else {
            return null;
        }
        // at this point, context, scope (if containing) have been extracted.
        int propValueIndex = propAtom.indexOf("=");
        if (propValueIndex == -1) {
            return null;
        }
        propertyName = propAtom.substring(0, propValueIndex);
        propertyValue = propAtom.substring(propValueIndex + 1);
        return new Prop(context, scope, propertyName, propertyValue, true);
    }

    /**
     * Extract all arguments from {@code args} starting with {@literal -P}, parsing them in the format of
     * {@literal context[#scope].propertyName=propertyValue} into {@link Prop} objects, mapping then by context and
     * property name.  The extracted ad-hoc property map is stored in {@code props} and the
     * rest of the arguments are stored in {@code args}.
     * @param args to parse and will contain the non {@literal -P} arguments after method execution
     * @param props will contain the ad-hoc properties parsed out of {@code args} after method execution.
     */
    public static void splitArgs(AtomicReference<String[]> args, AtomicReference<Map<String, Map<String, Prop>>> props) {
        String[] splitArgs = args.get();
        List<String> purged = new ArrayList<String>(splitArgs.length);
        Map<String, Map<String, Prop>> adHocProps = new HashMap<String, Map<String, Prop>>(2);
        for (String arg : splitArgs) {
            if (arg.startsWith("-P")) {
                parse(arg.substring(2), adHocProps);
            } else {
                purged.add(arg);
            }
        }
        args.set(purged.toArray(new String[purged.size()]));
        props.set(adHocProps);
    }

    /**
     * Updates values of the map of ad-hoc properties used during script execution to include {@code adHocProps}
     * (overriding existing values or adding if there are no existing values).
     * @param adHocProps the properties with which to update the map of ad-hoc properties used during script execution.
     */
    public static void updateAdHocProps(Map<String, Map<String, Prop>> adHocProps) {
        if ((adHocProps != null) && !adHocProps.isEmpty()) {
            Loader.setAdHocProps(adHocProps);
        }
    }

    private static void parse(String propAtom, Map<String, Map<String, Prop>> props) {
        Prop prop = PropsExt.parse(propAtom);
        if (prop == null) {
            Output.print(
                    "^warn^ Ad hoc property ^b^%s^r^ not of correct format ^b^context[#scope].propName=propValue^r^.",
                    propAtom);
            return;
        }
        Map<String, Prop> contextProps = props.get(prop.getContextScope());
        if (contextProps == null) {
            contextProps = new HashMap<String, Prop>(2);
            props.put(prop.getContextScope(), contextProps);
        }
        contextProps.put(prop.name, prop);
    }

    /**
     * Exposes {@link Loader#loadProjectProps(java.io.File, String)} for the {@link net.ocheyedan.ply.Init}
     * @param projectConfigDir from which to retrieve properties
     * @return the properties
     */
    public static Map<String, Map<String, Prop>> loadProjectProps(File projectConfigDir) {
        Map<String, Map<String, Prop>> props = new HashMap<String, Map<String, Prop>>();
        Loader.resolvePropertiesFromDirectory(projectConfigDir, true, props);
        return props;
    }

    private PropsExt() { }

}
