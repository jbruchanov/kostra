# Kostra Sample App

This sample is made to show real usage of the kostra library.

Project setup
```mermaid
flowchart BT
    shared[shared] -->shared1[shared-lib1]
    shared[shared] -->shared2[shared-lib2]
    android[appAndroid] -->shared
    ios[appIos] -->shared
    jvmDesktop[appJvmDesktop] -->shared
    jvmConsole[appJvmConsole] -->shared
    native[appNative]
```

## Internals

#### ResourceKey types
Kostra has multiple `ResourceKey` types to use the typing system to its advantage. The following class diagram represents interfaces coming from `kostra-common`.
KGP generates the same structure in each module to have strong binding between `K` object references and generated defaults.

```mermaid
classDiagram
    class ResourceKey
    ResourceKey : +Int key

    ResourceKey <|-- StringResourceKey
    ResourceKey <|-- AssetResourceKey
    ResourceKey <|-- PluralResourceKey
    AssetResourceKey <|-- PainterResourceKey
    AssetResourceKey <|-- BinaryResourceKey

    <<interface>> ResourceKey
    <<interface>> AssetResourceKey
    <<interface>> StringResourceKey
    <<interface>> PluralResourceKey
    <<interface>> PainterResourceKey
    <<interface>> BinaryResourceKey
```

As you can see in generated code, the kostra is using internally simple indexes to each particular record stored in kostra DB.
So the `K` object can look something like this:
```kotlin
//K object for Lib2
object KLib2 {
  object string {
    val textSimple = StringResourceKey(key = 0)
  }
  object plural {
    val textPlural = PluralResourceKey(key = 0)
  }
  object images {
    val capitalCity = PainterResourceKey(key = 1)
  }
  object xmls {
     val rawData = BinaryResourceKey(key = 2)
  }
}
```
All `ResourceKey` value classes are generated into the `kClassName` package to avoid mistakes like
`Lib1Resource.string.get(KLib2.string.textSimple)`. This code looks valid, but it's semantically incorrect, because `Lib1Resources` are
being used with the `KLib2` object.

What is important is the `key` aka DB index, which might work if the key exists in the other database. Obviously the output would be wrong. Generated defaults
prevent this via the typing system.
```kotlin
//compile time error
com.sample.lib1.stringResource(KLib2.string.textSimple)

//using Raw API compiles fine and will return wrong value or might lead to crash if DB key undefined
Lib1Resource.string.get(KLib2.string.textSimple)
```
Signature looks like this `com.sample.lib1.stringResource(key: com.sample.lib1.StringResourceKey) : String`, and
`KLib2.string.textSimple` is of type `com.sample.lib2.StringResourceKey`. Same simple name, different package. The compiler will let you know
of misuse of `Resources` and `K` object references if you are using generated defaults.

## MultiModule setup
As you can see, there is `shared-lib1` as a lib with a compose reference, and `shared-lib2` as a plain library.

The first important thing to understand is that **everything Kostra does is file based**.
Resources and internal databases are stored as simple files and as part of the resources bundle which is included in the final release product.

*Each module generates "same" code based on given data.*<br/>
**There is no real resource merging**.

#### kClassName
Unique `KClassName` is important to put everything generated into `com.sample.lib1` package to avoid any duplicates.<br/>


#### Module Prefix
To prevent any file collision, kostra has extra `kostra.modulePrefix` KGP config to help with that.
```groovy
//shared-lib1/build.gradle
kostra {
    modulePrefix = "Lib1"
    resourcesDefaults.value([ResourcesDefaults.ComposeGetters])
    KClassName = "com.sample.lib1.KLib1"
}
```
Specific `modulePrefix` value has a side effect for the resources analysis, so they can be stored:
```shell
sample/shared-lib1/src/commonMain/resources/lib1/group/image.png
```
*Notice the extra `lib1` subdirectory.*
<br/><br/> With no `modulePrefix`, the `image.png` would fall into the `lib1` group referenceable via `K.lib1.image`. Adding `kostra.modulePrefix = "Lib1"` makes
the 1st resources subdirectory ignored if it matches (case-insensitive) the `modulePrefix`, and it becomes `K.group.image`.
Having all resources saved in a `lib1` subdirectory prevents you from having potential duplicates in the final product.

**Obviously, `modulePrefix` and package from `KClassName` must be unique per each module.**

#### ResourceDefaults

Using `ResourcesDefaults.ComposeCommon` and `ResourcesDefaults.Common` defaults might be annoying for usage in a multimodule setup.
Having a screen using multiple resources coming from different modules, you will end up having as many imported default functions like `stringResource` as your number of used
module resources is. Autocomplete in the file becomes annoying because of a lot of identically named functions offered.

For this case, it's better to use `ResourcesDefaults.ComposeGetters` or `ResourcesDefaults.Getters`. KGP config
`kostra.resourcesDefaults.value(listOf(com.jibru.kostra.plugin.ResourcesDefaults.ComposeGetters))` makes KGP generate only specified variant of getters.

Your code will be then using extension functions on the `ResourceKey` types.
```kotlin
println(KLib1.string.textLib1.get())
println(KLib2.string.textLib2.get())
```

#### Interfaces

KGP has an extra setting to generate interfaces for `K` objects for easier class delegation if necessary for a "merged look-alike" `K` object.
Just enable it via `kostra.interfaces = true`; it's enabled automatically if there is a non-empty `kostra.modulePrefix`.

#### Class delegation

With interfaces generated, a global `KM` object can be made simply using class delegation.
```kotlin
object KM {
    object string :
        IKLib1.string by KLib1.string,
        IKLib2.string by KLib2.string,
        IK.string by K.string

    object images :
        IKLib1.root by KLib1.root,
        IKLib2.root by KLib2.root,
        IK.images by K.images
}
//which can be used simply as
println(KM.images.lib1Text.get())
println(KM.images.lib2Text.get())
```
Having a simple `KM` object with all merged resources makes coding UX nicer and hides multiple resource origins. <br/>
`kostra.keyMapper` might help to add extra logic for keys.

*All keys must be unique, otherwise the class delegation will fail due to identical names with different return types, which can't be
solved in kotlin.*

#### Internals
In case of isolated module, everything kostra generated can be marked with `internal` visibility accessor. Just use
`kostra.internalVisibility = true`. All DBs must be part of the final release product and they will be still accessible like any other bundled resource.

#### iOS App — Signing Setup

The iOS Xcode project does **not** commit signing details. Each developer drops their own
team ID and bundle ID into a gitignored `Local.xcconfig` so the sample stays portable.

First-time setup:
```shell
cd sample/appIos
cp Local.xcconfig.template Local.xcconfig
# edit Local.xcconfig — set TEAM_ID to your Apple Developer team ID
#                     and BUNDLE_ID to a unique reverse-DNS string under your team
pod install
```

How it's wired:
- [`appIos.xcodeproj/project.pbxproj`](appIos/appIos.xcodeproj/project.pbxproj) references
  `${TEAM_ID}` and `${BUNDLE_ID}` instead of literal values.
- [`Podfile`](appIos/Podfile)'s `post_install` hook injects
  `#include? "../../../Local.xcconfig"` into the generated `Pods-appIos.*.xcconfig` files,
  so `Local.xcconfig` values flow through the Pods xcconfig chain into the app target.
- Without `Local.xcconfig` the build fails with a clean *"Signing requires a development team"*
  error — that's expected; create the file and re-run `pod install`.

Why this layout — Apple team IDs aren't strictly secret, but committing them ties the sample
to one developer's account and breaks the build for everyone else. `Local.xcconfig` keeps
the project clonable while letting each contributor sign with their own team.

Building for Apple-Silicon-only? The project sets
`EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` because the shared module only declares
`iosArm64()` and `iosSimulatorArm64()` Kotlin targets. Add `iosX64()` back to the relevant
`build.gradle` files and remove the `EXCLUDED_ARCHS` setting (from the project and from
`Podfile`'s `post_install`) if you need Intel-Mac simulator support.

#### IOS
KMP for iOS is currently the painful part. [KMP doesn't support any resource merging](https://github.com/JetBrains/compose-multiplatform/issues/3391) on any level.
It must be done manually, for example like this — see the real setup in
[sample/shared/build.gradle](shared/build.gradle).
```groovy
//shared/build.gradle

//simple reference for all depending modules
def depModules = [project(":shared-lib1"), project(":shared-lib2")]

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                //add a project module as a dependency, just like in any other case
                //'api' instead of 'implementation' is necessary for cocoapods export if you use them
                depModules.forEach {
                    api(it)
                }
            }
        }

        iosMain {
            //add explicit reference to use module resources + kostra resources as part of this build
            //similarly this can be done for any transitive dependency coming from outside of the project
            //the merged result for iosSimulator builds ends up in
            //kostra/sample/build/ios/Debug-iphonesimulator/appIos.app/compose-resources/
            depModules.forEach { Project p ->
                resources.srcDirs(new File(p.projectDir, "src/commonMain/resources"))
                resources.srcDirs(new File(p.layout.getBuildDirectory().get().asFile, "generated/kostra/resources"))
            }
        }
    }
}

//just ensure that lib DBs are created with the 'shared' module DB, probably unnecessary
tasks.getByName("generateDatabases")
    .dependsOn(":shared-lib1:generateDatabases", ":shared-lib2:generateDatabases")
```

#### Native App
This is just an MVP proof of concept, therefore not using any of the shared modules.
AFAIK there is currently no way to easily bundle the resources into your executable file.
There is also no resource merging for native. This example just copies all the resources into a build output directory.
Running the native app from the IDE will fail with an "Unable to find resources" exception; there seems to be no way to specify what should be the
working directory.

To test it, just run
`./gradlew appNativeConsole:assemble` and outputs go to `build/bin/native/releaseExecutable`. Running `./appNativeConsole` from there works as expected,
tested on `Windows`, `Ubuntu in Windows`, `MacOs`.

## License

[Apache License 2.0](https://github.com/jbruchanov/kostra/blob/develop/LICENSE)
