# Reusing the Forgejo APK Release Workflow

Use this checklist when copying `.forgejo/` into another Android app repository.
The detailed workflow behavior is documented in
[`release-apk-workflow.md`](release-apk-workflow.md).

## Plan

1. Copy `.forgejo/workflows/release-apk.yml` and `.forgejo/docs/` into the target
   Android repository.
2. Replace the project-specific names in the workflow and docs.
3. Confirm the target app can read release signing values from environment
   variables.
4. Add the required Forgejo secrets.
5. Validate the workflow syntax and the local release build before pushing a tag.

## Implementation

### Replace Project Names

Edit `.forgejo/workflows/release-apk.yml`:

```bash
keystore_path="${temp_dir}/PROJECT_SLUG-release.keystore"
export RELEASE_KEYSTORE_PATH="${temp_dir}/PROJECT_SLUG-release.keystore"
cp "${apk_files[0]}" "dist/PROJECT_SLUG-${tag}.apk"
asset_path="dist/PROJECT_SLUG-${tag}.apk"
release_name="APP_NAME ${tag}"
owner="firebadnofire"
repo="GITHUB_REPO_NAME"
```

Use a lowercase, shell-safe `PROJECT_SLUG` for filenames, such as `milestones` or
`calcount`. Use the exact GitHub repository name for `GITHUB_REPO_NAME`; GitHub
repository paths are case-sensitive enough that mismatches can produce confusing
404 errors.

Edit `.forgejo/docs/release-apk-workflow.md` to match the same app name, artifact
name, and GitHub repository.

### Remove Copied App Dependencies

Search for assumptions from the source project:

```bash
rg -n "trapmaster|simplewallet|upstream|PWA|PROJECT_SLUG|APP_NAME|GITHUB_REPO_NAME" .forgejo
```

Remove any source-app setup steps that the target app does not need. For a normal
native Android app, there should not be a step that fetches another app or web
asset before Gradle runs.

### Wire Release Signing in Gradle

The workflow exports these values before `assembleRelease`:

```text
RELEASE_KEYSTORE_PATH
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

The Android app must use them in its release signing config. If the target app
does not already do that, add a conditional signing config to the module
`build.gradle.kts`:

```kotlin
val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("KEY_ALIAS")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

signingConfigs {
    if (hasReleaseSigning) {
        create("release") {
            storeFile = file(releaseKeystorePath!!)
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
}

buildTypes {
    release {
        if (hasReleaseSigning) {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

Keep the app's existing `isMinifyEnabled`, `proguardFiles`, and other release
settings. Do not hardcode passwords, aliases, tokens, or keystore paths.

### Configure Secrets

Add these secrets in Forgejo for the target repository or owning organization:

```text
KEY_ALIAS
KEY_PASSWORD
KEYSTORE_BASE64
KEYSTORE_PASSWORD
GH_KEY
```

`GH_KEY` must be able to create and edit releases and upload release assets in
the GitHub target repository. A fine-grained GitHub token should have repository
`Contents: Read and write`.

Generate `KEYSTORE_BASE64` from the keystore file with:

```bash
base64 -i release.keystore
```

Use the output as the secret value. Do not commit the keystore.

## Validation

Run these checks in the target repository before pushing a release tag:

```bash
ruby -e 'require "yaml"; YAML.load_file(".forgejo/workflows/release-apk.yml"); puts "yaml ok"'
rg -n "trapmaster|simplewallet|upstream|PWA|PROJECT_SLUG|APP_NAME|GITHUB_REPO_NAME" .forgejo
GRADLE_USER_HOME="$PWD/.gradle" sh ./gradlew --no-daemon tasks --all
GRADLE_USER_HOME="$PWD/.gradle" sh ./gradlew --no-daemon assembleRelease
```

The local `assembleRelease` may produce an unsigned APK if signing secrets are not
present. That is acceptable for local validation. The CI job should produce a
signed APK when the secrets are configured.

After validation, push a version tag:

```bash
git tag vX.Y.Z
git push origin vX.Y.Z
```

If GitHub release publishing fails with a 404, check the `owner` and `repo`
values first, then verify that `GH_KEY` has access to that repository.
