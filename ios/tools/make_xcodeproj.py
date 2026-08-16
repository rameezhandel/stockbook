#!/usr/bin/env python3
"""Generates ios/Stockbook.xcodeproj in Xcode 14-compatible format.

    python3 ios/tools/make_xcodeproj.py

Why this exists
---------------
The project was originally hand-written in Xcode 16's format — objectVersion 77
with PBXFileSystemSynchronizedRootGroup, the feature that lets a target pick up
files from a folder without listing them. Xcode 14 cannot open that at all:
"the project is in a future Xcode project file format", with no way to convert
it from the older Xcode.

objectVersion 56 has no synchronized groups, so every file must be listed
explicitly — which is exactly the tedium that makes a hand-maintained pbxproj
rot. Hence a generator: add a .swift file anywhere under Stockbook/ or
StockbookTests/, re-run this, and the project is correct again.

Keep it in step with project.yml, which describes the same two targets for
XcodeGen.
"""

import pathlib
import hashlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
PROJECT = ROOT / "Stockbook.xcodeproj"

DEPLOYMENT_TARGET = "16.0"
SWIFT_VERSION = "5.7"


def uid(*parts: str) -> str:
    """A stable 24-hex identifier derived from the path, so regenerating the
    project produces the same file rather than a diff of shuffled ids."""
    digest = hashlib.sha1("::".join(parts).encode()).hexdigest()
    return digest[:24].upper()


def sources(folder: str) -> list[pathlib.Path]:
    return sorted((ROOT / folder).rglob("*.swift"))


def resources(folder: str) -> list[pathlib.Path]:
    # Asset catalogues are referenced as single bundles, not their contents.
    return sorted((ROOT / folder).rglob("*.xcassets"))


def build_tree(paths: list[pathlib.Path], root: str) -> dict:
    """Nested dict mirroring the folder layout, so Xcode's navigator matches
    the filesystem."""
    tree: dict = {}
    for path in paths:
        parts = path.relative_to(ROOT / root).parts
        node = tree
        for part in parts[:-1]:
            node = node.setdefault(part, {})
        node.setdefault("__files__", []).append(path)
    return tree


def emit_group(name: str, node: dict, root: str, out: list, path_prefix: str = "") -> str:
    """Emits PBXGroups depth-first and returns this group's id."""
    children = []

    for child_name in sorted(k for k in node if k != "__files__"):
        child_path = f"{path_prefix}/{child_name}" if path_prefix else child_name
        children.append(emit_group(child_name, node[child_name], root, out, child_path))

    for file in node.get("__files__", []):
        children.append(uid("file", str(file)))

    group_id = uid("group", root, path_prefix or name)
    kids = "\n".join(f"\t\t\t\t{c},"
                     for c in children)
    out.append(
        f"\t\t{group_id} /* {name} */ = {{\n"
        f"\t\t\tisa = PBXGroup;\n"
        f"\t\t\tchildren = (\n{kids}\n\t\t\t);\n"
        f"\t\t\tpath = {name};\n"
        f"\t\t\tsourceTree = \"<group>\";\n"
        f"\t\t}};"
    )
    return group_id


def generate() -> str:
    app_sources = sources("Stockbook")
    app_resources = resources("Stockbook")
    test_sources = sources("StockbookTests")

    app_target = uid("target", "Stockbook")
    test_target = uid("target", "StockbookTests")
    project_id = uid("project")
    main_group = uid("group", "main")
    products_group = uid("group", "Products")
    app_product = uid("product", "app")
    test_product = uid("product", "tests")

    file_refs, build_files, group_blocks = [], [], []

    for file in app_sources + test_sources + app_resources:
        ref = uid("file", str(file))
        kind = "folder.assetcatalog" if file.suffix == ".xcassets" else "sourcecode.swift"
        file_refs.append(
            f'\t\t{ref} /* {file.name} */ = {{isa = PBXFileReference; '
            f'lastKnownFileType = {kind}; path = {file.name}; sourceTree = "<group>"; }};'
        )
        build = uid("build", str(file))
        build_files.append(
            f'\t\t{build} /* {file.name} in Build */ = {{isa = PBXBuildFile; '
            f'fileRef = {ref} /* {file.name} */; }};'
        )

    app_group = emit_group("Stockbook", build_tree(app_sources + app_resources, "Stockbook"),
                           "Stockbook", group_blocks)
    test_group = emit_group("StockbookTests", build_tree(test_sources, "StockbookTests"),
                            "StockbookTests", group_blocks)

    def phase_files(files):
        return "\n".join(
            f'\t\t\t\t{uid("build", str(f))} /* {f.name} in Build */,' for f in files
        )

    app_sources_phase = uid("phase", "app", "sources")
    app_resources_phase = uid("phase", "app", "resources")
    app_frameworks_phase = uid("phase", "app", "frameworks")
    test_sources_phase = uid("phase", "tests", "sources")
    test_resources_phase = uid("phase", "tests", "resources")
    test_frameworks_phase = uid("phase", "tests", "frameworks")
    dependency = uid("dependency")
    proxy = uid("proxy")

    project_config_list = uid("configlist", "project")
    app_config_list = uid("configlist", "app")
    test_config_list = uid("configlist", "tests")

    def configuration(config_id, name, settings):
        body = "\n".join(f"\t\t\t\t{k} = {v};" for k, v in settings.items())
        return (f"\t\t{config_id} /* {name} */ = {{\n"
                f"\t\t\tisa = XCBuildConfiguration;\n"
                f"\t\t\tbuildSettings = {{\n{body}\n\t\t\t}};\n"
                f"\t\t\tname = {name};\n\t\t}};")

    shared = {
        "ALWAYS_SEARCH_USER_PATHS": "NO",
        "CLANG_ENABLE_MODULES": "YES",
        "CLANG_ENABLE_OBJC_ARC": "YES",
        "ENABLE_STRICT_OBJC_MSGSEND": "YES",
        "GCC_NO_COMMON_BLOCKS": "YES",
        "IPHONEOS_DEPLOYMENT_TARGET": DEPLOYMENT_TARGET,
        "SDKROOT": "iphoneos",
        "SWIFT_VERSION": SWIFT_VERSION,
    }
    debug = dict(shared, ONLY_ACTIVE_ARCH="YES", ENABLE_TESTABILITY="YES",
                 SWIFT_OPTIMIZATION_LEVEL='"-Onone"',
                 SWIFT_ACTIVE_COMPILATION_CONDITIONS="DEBUG",
                 GCC_OPTIMIZATION_LEVEL="0", DEBUG_INFORMATION_FORMAT="dwarf")
    release = dict(shared, SWIFT_COMPILATION_MODE="wholemodule",
                   DEBUG_INFORMATION_FORMAT='"dwarf-with-dsym"',
                   ENABLE_NS_ASSERTIONS="NO", VALIDATE_PRODUCT="YES")

    app = {
        "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon",
        "CODE_SIGN_STYLE": "Automatic",
        "CURRENT_PROJECT_VERSION": "1",
        "GENERATE_INFOPLIST_FILE": "YES",
        "INFOPLIST_KEY_UILaunchScreen_Generation": "YES",
        "INFOPLIST_KEY_UIRequiresFullScreen": "YES",
        "INFOPLIST_KEY_UIStatusBarStyle": "UIStatusBarStyleLightContent",
        "INFOPLIST_KEY_UISupportedInterfaceOrientations": "UIInterfaceOrientationPortrait",
        "INFOPLIST_KEY_UIUserInterfaceStyle": "Dark",
        "INFOPLIST_KEY_ITSAppUsesNonExemptEncryption": "NO",
        "LD_RUNPATH_SEARCH_PATHS": '(\n\t\t\t\t\t"$(inherited)",\n\t\t\t\t\t"@executable_path/Frameworks",\n\t\t\t\t)',
        "MARKETING_VERSION": "1.0",
        "PRODUCT_BUNDLE_IDENTIFIER": "com.stockbook.app",
        "PRODUCT_NAME": '"$(TARGET_NAME)"',
        "TARGETED_DEVICE_FAMILY": "1",
    }
    tests = {
        "BUNDLE_LOADER": '"$(TEST_HOST)"',
        "CODE_SIGN_STYLE": "Automatic",
        "CURRENT_PROJECT_VERSION": "1",
        "GENERATE_INFOPLIST_FILE": "YES",
        "MARKETING_VERSION": "1.0",
        "PRODUCT_BUNDLE_IDENTIFIER": "com.stockbook.app.tests",
        "PRODUCT_NAME": '"$(TARGET_NAME)"',
        "SWIFT_VERSION": SWIFT_VERSION,
        "TARGETED_DEVICE_FAMILY": "1",
        "TEST_HOST": '"$(BUILT_PRODUCTS_DIR)/Stockbook.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/Stockbook"',
    }

    ids = {n: uid("config", n) for n in
           ["pd", "pr", "ad", "ar", "td", "tr"]}
    configs = [
        configuration(ids["pd"], "Debug", debug),
        configuration(ids["pr"], "Release", release),
        configuration(ids["ad"], "Debug", app),
        configuration(ids["ar"], "Release", app),
        configuration(ids["td"], "Debug", tests),
        configuration(ids["tr"], "Release", tests),
    ]

    def config_list(list_id, label, debug_id, release_id):
        return (f'\t\t{list_id} /* Build configuration list for {label} */ = {{\n'
                f"\t\t\tisa = XCConfigurationList;\n"
                f"\t\t\tbuildConfigurations = (\n"
                f"\t\t\t\t{debug_id} /* Debug */,\n\t\t\t\t{release_id} /* Release */,\n\t\t\t);\n"
                f"\t\t\tdefaultConfigurationIsVisible = 0;\n"
                f"\t\t\tdefaultConfigurationName = Release;\n\t\t}};")

    nl = "\n"
    text = f"""// !$*UTF8*$!
{{
\tarchiveVersion = 1;
\tclasses = {{
\t}};
\tobjectVersion = 56;
\tobjects = {{

/* Begin PBXBuildFile section */
{nl.join(build_files)}
/* End PBXBuildFile section */

/* Begin PBXContainerItemProxy section */
\t\t{proxy} /* PBXContainerItemProxy */ = {{
\t\t\tisa = PBXContainerItemProxy;
\t\t\tcontainerPortal = {project_id} /* Project object */;
\t\t\tproxyType = 1;
\t\t\tremoteGlobalIDString = {app_target};
\t\t\tremoteInfo = Stockbook;
\t\t}};
/* End PBXContainerItemProxy section */

/* Begin PBXFileReference section */
{nl.join(file_refs)}
\t\t{app_product} /* Stockbook.app */ = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = Stockbook.app; sourceTree = BUILT_PRODUCTS_DIR; }};
\t\t{test_product} /* StockbookTests.xctest */ = {{isa = PBXFileReference; explicitFileType = wrapper.cfbundle; includeInIndex = 0; path = StockbookTests.xctest; sourceTree = BUILT_PRODUCTS_DIR; }};
/* End PBXFileReference section */

/* Begin PBXFrameworksBuildPhase section */
\t\t{app_frameworks_phase} /* Frameworks */ = {{
\t\t\tisa = PBXFrameworksBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
\t\t{test_frameworks_phase} /* Frameworks */ = {{
\t\t\tisa = PBXFrameworksBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
\t\t{main_group} = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
\t\t\t\t{app_group} /* Stockbook */,
\t\t\t\t{test_group} /* StockbookTests */,
\t\t\t\t{products_group} /* Products */,
\t\t\t);
\t\t\tsourceTree = "<group>";
\t\t}};
\t\t{products_group} /* Products */ = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
\t\t\t\t{app_product} /* Stockbook.app */,
\t\t\t\t{test_product} /* StockbookTests.xctest */,
\t\t\t);
\t\t\tname = Products;
\t\t\tsourceTree = "<group>";
\t\t}};
{nl.join(group_blocks)}
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
\t\t{app_target} /* Stockbook */ = {{
\t\t\tisa = PBXNativeTarget;
\t\t\tbuildConfigurationList = {app_config_list} /* Build configuration list for PBXNativeTarget "Stockbook" */;
\t\t\tbuildPhases = (
\t\t\t\t{app_sources_phase} /* Sources */,
\t\t\t\t{app_frameworks_phase} /* Frameworks */,
\t\t\t\t{app_resources_phase} /* Resources */,
\t\t\t);
\t\t\tbuildRules = (
\t\t\t);
\t\t\tdependencies = (
\t\t\t);
\t\t\tname = Stockbook;
\t\t\tproductName = Stockbook;
\t\t\tproductReference = {app_product} /* Stockbook.app */;
\t\t\tproductType = "com.apple.product-type.application";
\t\t}};
\t\t{test_target} /* StockbookTests */ = {{
\t\t\tisa = PBXNativeTarget;
\t\t\tbuildConfigurationList = {test_config_list} /* Build configuration list for PBXNativeTarget "StockbookTests" */;
\t\t\tbuildPhases = (
\t\t\t\t{test_sources_phase} /* Sources */,
\t\t\t\t{test_frameworks_phase} /* Frameworks */,
\t\t\t\t{test_resources_phase} /* Resources */,
\t\t\t);
\t\t\tbuildRules = (
\t\t\t);
\t\t\tdependencies = (
\t\t\t\t{dependency} /* PBXTargetDependency */,
\t\t\t);
\t\t\tname = StockbookTests;
\t\t\tproductName = StockbookTests;
\t\t\tproductReference = {test_product} /* StockbookTests.xctest */;
\t\t\tproductType = "com.apple.product-type.bundle.unit-test";
\t\t}};
/* End PBXNativeTarget section */

/* Begin PBXProject section */
\t\t{project_id} /* Project object */ = {{
\t\t\tisa = PBXProject;
\t\t\tattributes = {{
\t\t\t\tBuildIndependentTargetsInParallel = 1;
\t\t\t\tLastSwiftUpdateCheck = 1420;
\t\t\t\tLastUpgradeCheck = 1420;
\t\t\t\tTargetAttributes = {{
\t\t\t\t\t{app_target} = {{
\t\t\t\t\t\tCreatedOnToolsVersion = 14.2;
\t\t\t\t\t}};
\t\t\t\t\t{test_target} = {{
\t\t\t\t\t\tCreatedOnToolsVersion = 14.2;
\t\t\t\t\t\tTestTargetID = {app_target};
\t\t\t\t\t}};
\t\t\t\t}};
\t\t\t}};
\t\t\tbuildConfigurationList = {project_config_list} /* Build configuration list for PBXProject "Stockbook" */;
\t\t\tcompatibilityVersion = "Xcode 14.0";
\t\t\tdevelopmentRegion = en;
\t\t\thasScannedForEncodings = 0;
\t\t\tknownRegions = (
\t\t\t\ten,
\t\t\t\tBase,
\t\t\t);
\t\t\tmainGroup = {main_group};
\t\t\tproductRefGroup = {products_group} /* Products */;
\t\t\tprojectDirPath = "";
\t\t\tprojectRoot = "";
\t\t\ttargets = (
\t\t\t\t{app_target} /* Stockbook */,
\t\t\t\t{test_target} /* StockbookTests */,
\t\t\t);
\t\t}};
/* End PBXProject section */

/* Begin PBXResourcesBuildPhase section */
\t\t{app_resources_phase} /* Resources */ = {{
\t\t\tisa = PBXResourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{phase_files(app_resources)}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
\t\t{test_resources_phase} /* Resources */ = {{
\t\t\tisa = PBXResourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
/* End PBXResourcesBuildPhase section */

/* Begin PBXSourcesBuildPhase section */
\t\t{app_sources_phase} /* Sources */ = {{
\t\t\tisa = PBXSourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{phase_files(app_sources)}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
\t\t{test_sources_phase} /* Sources */ = {{
\t\t\tisa = PBXSourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{phase_files(test_sources)}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
/* End PBXSourcesBuildPhase section */

/* Begin PBXTargetDependency section */
\t\t{dependency} /* PBXTargetDependency */ = {{
\t\t\tisa = PBXTargetDependency;
\t\t\ttarget = {app_target} /* Stockbook */;
\t\t\ttargetProxy = {proxy} /* PBXContainerItemProxy */;
\t\t}};
/* End PBXTargetDependency section */

/* Begin XCBuildConfiguration section */
{nl.join(configs)}
/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
{config_list(project_config_list, 'PBXProject "Stockbook"', ids["pd"], ids["pr"])}
{config_list(app_config_list, 'PBXNativeTarget "Stockbook"', ids["ad"], ids["ar"])}
{config_list(test_config_list, 'PBXNativeTarget "StockbookTests"', ids["td"], ids["tr"])}
/* End XCConfigurationList section */
\t}};
\trootObject = {project_id} /* Project object */;
}}
"""
    return text




def generate_scheme() -> str:
    """The scheme references target ids, so it is generated alongside them —
    a stale BlueprintIdentifier silently produces an unbuildable scheme."""
    app_target = uid("target", "Stockbook")
    test_target = uid("target", "StockbookTests")
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion = "1420" version = "1.7">
   <BuildAction parallelizeBuildables = "YES" buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry buildForTesting = "YES" buildForRunning = "YES" buildForProfiling = "YES" buildForArchiving = "YES" buildForAnalyzing = "YES">
            <BuildableReference BuildableIdentifier = "primary" BlueprintIdentifier = "{app_target}" BuildableName = "Stockbook.app" BlueprintName = "Stockbook" ReferencedContainer = "container:Stockbook.xcodeproj">
            </BuildableReference>
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction buildConfiguration = "Debug" selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv = "YES">
      <Testables>
         <TestableReference skipped = "NO">
            <BuildableReference BuildableIdentifier = "primary" BlueprintIdentifier = "{test_target}" BuildableName = "StockbookTests.xctest" BlueprintName = "StockbookTests" ReferencedContainer = "container:Stockbook.xcodeproj">
            </BuildableReference>
         </TestableReference>
      </Testables>
   </TestAction>
   <LaunchAction buildConfiguration = "Debug" selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB" launchStyle = "0" useCustomWorkingDirectory = "NO" ignoresPersistentStateOnLaunch = "NO" debugDocumentVersioning = "YES" debugServiceExtension = "internal" allowLocationSimulation = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         <BuildableReference BuildableIdentifier = "primary" BlueprintIdentifier = "{app_target}" BuildableName = "Stockbook.app" BlueprintName = "Stockbook" ReferencedContainer = "container:Stockbook.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction buildConfiguration = "Release" shouldUseLaunchSchemeArgsEnv = "YES" savedToolIdentifier = "" useCustomWorkingDirectory = "NO" debugDocumentVersioning = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         <BuildableReference BuildableIdentifier = "primary" BlueprintIdentifier = "{app_target}" BuildableName = "Stockbook.app" BlueprintName = "Stockbook" ReferencedContainer = "container:Stockbook.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </ProfileAction>
   <AnalyzeAction buildConfiguration = "Debug"></AnalyzeAction>
   <ArchiveAction buildConfiguration = "Release" revealArchiveInOrganizer = "YES"></ArchiveAction>
</Scheme>
"""

if __name__ == "__main__":
    PROJECT.mkdir(parents=True, exist_ok=True)
    (PROJECT / "project.pbxproj").write_text(generate())

    schemes = PROJECT / "xcshareddata" / "xcschemes"
    schemes.mkdir(parents=True, exist_ok=True)
    (schemes / "Stockbook.xcscheme").write_text(generate_scheme())

    workspace = PROJECT / "project.xcworkspace"
    workspace.mkdir(parents=True, exist_ok=True)
    (workspace / "contents.xcworkspacedata").write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n<Workspace version = "1.0">\n'
        '   <FileRef location = "self:"></FileRef>\n</Workspace>\n'
    )
    counts = (len(sources("Stockbook")), len(sources("StockbookTests")), len(resources("Stockbook")))
    print(f"wrote {PROJECT/'project.pbxproj'}")
    print(f"  {counts[0]} app sources, {counts[1]} test sources, {counts[2]} asset catalogue(s)")
    print(f"  iOS {DEPLOYMENT_TARGET}, Swift {SWIFT_VERSION}, objectVersion 56")
