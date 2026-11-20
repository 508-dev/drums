#!/usr/bin/env bash
# Generate an app's release signing key, store it in pass, and install the
# release secrets into the GitHub environment the release workflow uses.
#
#   setup-release-signing.sh <app-slug> --repo <owner/repo> [options]
#
# Options:
#   --env <name>          GitHub environment for the secrets (default: Prod)
#   --pass-prefix <path>  pass folder that holds <app-slug>/ (default: 508/android)
#   --with-release-please-token
#                         also copy <prefix>/<app-slug>/RELEASE_PLEASE_TOKEN from
#                         pass into the environment (decrypts it, so gpg may prompt)
#   --release-please-token-only
#                         skip the key entirely; only copy RELEASE_PLEASE_TOKEN
#   --dry-run             generate a throwaway key into a temporary pass store and
#                         report what would be sent to GitHub, without sending it
#
# What a normal run does:
#   1. Refuses to continue if any release entry already exists in pass, or if
#      RELEASE_KEYSTORE_BASE64 already exists in the GitHub environment. The
#      release key is the app's permanent identity: Android won't update an
#      installed app with an APK signed by a different key. So this script never
#      replaces one. To rotate a key before the first release, delete the old
#      entries by hand first.
#   2. Generates a PKCS12 keystore (RSA 4096, valid 10000 days, alias <app-slug>)
#      with a random 40-character password. This happens in a private temporary
#      directory that is deleted on exit.
#   3. Stores the key in pass under <prefix>/<app-slug>/, named exactly after the
#      GitHub secrets: RELEASE_KEYSTORE_BASE64, RELEASE_KEYSTORE_PASSWORD,
#      RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD. The last holds the keystore
#      password again, because PKCS12 keystores have a single password.
#   4. Sets the same four secrets in the GitHub environment, creating the
#      environment if it doesn't exist.
#   5. Prints the certificate's SHA-256 fingerprint, which is public.
#
# Secret values never appear in output or in process arguments (where `ps`
# would show them). They travel only through stdin and environment variables.
# pass commits each entry to its git repository but doesn't push. Run
# `pass git push` afterwards so the key is backed up off this machine.
#
# RELEASE_PLEASE_TOKEN is a fine-grained personal access token, and GitHub only
# lets you create those in the web UI. Scope it to the repository only, with
# Contents and Pull requests set to read and write. Save it with
# `pass insert <prefix>/<app-slug>/RELEASE_PLEASE_TOKEN`, then run this script
# again with --release-please-token-only.
#
# Run this yourself as the maintainer. An agent may run it only when the
# maintainer explicitly asks; signing keys are never created as a side effect
# of other work.
set -euo pipefail

# Everything this script writes is secret, so use owner-only permissions from
# the first byte.
umask 077

usage() {
    cat >&2 <<EOF
usage: ${BASH_SOURCE[0]##*/} <app-slug> --repo <owner/repo> [--env <name>]
         [--pass-prefix <path>] [--with-release-please-token |
          --release-please-token-only] [--dry-run]
EOF
    exit 2
}

[ "$#" -ge 1 ] || usage
slug=$1
shift

repo=
env_name=Prod
prefix=508/android
with_token=false
token_only=false
dry_run=false
while [ "$#" -gt 0 ]; do
    case $1 in
        --repo)
            [ "$#" -ge 2 ] || usage
            repo=$2
            shift 2
            ;;
        --env)
            [ "$#" -ge 2 ] || usage
            env_name=$2
            shift 2
            ;;
        --pass-prefix)
            [ "$#" -ge 2 ] || usage
            prefix=${2%/}
            shift 2
            ;;
        --with-release-please-token)
            with_token=true
            shift
            ;;
        --release-please-token-only)
            token_only=true
            shift
            ;;
        --dry-run)
            dry_run=true
            shift
            ;;
        *)
            usage
            ;;
    esac
done

# The slug becomes a pass path component and the keytool alias, so keep it to
# lowercase letters, digits and hyphens.
[[ $slug =~ ^[a-z0-9][a-z0-9-]*$ ]] || {
    echo "error: app-slug must match [a-z0-9][a-z0-9-]* (lowercase), got '$slug'" >&2
    exit 2
}
[[ $repo =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || {
    echo "error: --repo <owner/repo> is required" >&2
    exit 2
}

need() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "error: $1 not found on PATH" >&2
        exit 1
    }
}
need pass
need gh
need base64

# keytool lives in the JDK, which may not be on PATH when only JAVA_HOME is set.
if command -v keytool >/dev/null 2>&1; then
    keytool=keytool
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
    keytool="$JAVA_HOME/bin/keytool"
else
    echo "error: keytool not found on PATH or under \$JAVA_HOME/bin" >&2
    exit 1
fi

store_dir=${PASSWORD_STORE_DIR:-$HOME/.password-store}
[ -f "$store_dir/.gpg-id" ] || {
    echo "error: no pass store at $store_dir (run \`pass init\` first)" >&2
    exit 1
}
entry_dir="$prefix/$slug"
names=(RELEASE_KEYSTORE_BASE64 RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD)

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

if $dry_run; then
    # A throwaway store encrypted to the same recipients. Encrypting only needs
    # the public keys, and the real store is never touched.
    real_store=$store_dir
    store_dir="$tmp/pass-store"
    export PASSWORD_STORE_DIR=$store_dir
    # shellcheck disable=SC2046  # one recipient per line in .gpg-id
    pass init $(cat "$real_store/.gpg-id") >/dev/null
    echo "==> dry run: using a temporary pass store; nothing is sent to GitHub"
fi

# Reads the secret value from stdin.
gh_set() {
    if $dry_run; then
        cat >/dev/null
        echo "    (dry run) would set $1 in the $env_name environment on $repo"
    else
        gh secret set "$1" --env "$env_name" -R "$repo"
    fi
}

# Reads the entry's contents from stdin.
pass_put() {
    pass insert -m "$entry_dir/$1" >/dev/null
}

if ! $token_only; then
    # --- 1. never replace an existing release key --------------------------
    for name in "${names[@]}"; do
        if [ -e "$store_dir/$entry_dir/$name.gpg" ]; then
            echo "error: pass entry $entry_dir/$name already exists; refusing to replace a release key" >&2
            exit 1
        fi
    done
    # Listing fails when the environment doesn't exist yet; that just means no
    # secrets.
    existing=$(gh secret list --env "$env_name" -R "$repo" --json name --jq '.[].name' 2>/dev/null || true)
    if grep -qx RELEASE_KEYSTORE_BASE64 <<<"$existing"; then
        echo "error: RELEASE_KEYSTORE_BASE64 already exists in the $env_name environment on $repo; refusing to replace a release key" >&2
        exit 1
    fi

    # --- 2. generate ---------------------------------------------------------
    # 40 random alphanumeric characters. `|| true` absorbs the SIGPIPE that
    # `head` sends `tr` once it has the bytes it needs.
    password=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 40 || true)
    [ "${#password}" -eq 40 ] || {
        echo "error: failed to generate a password" >&2
        exit 1
    }
    keystore="$tmp/release.p12"
    # Pass the password through the environment so it never appears in `ps`.
    KEYSTORE_PASS="$password" "$keytool" -genkeypair -noprompt \
        -keystore "$keystore" \
        -storetype PKCS12 \
        -alias "$slug" \
        -keyalg RSA -keysize 4096 \
        -validity 10000 \
        -dname "CN=${slug}, O=508.dev" \
        -storepass:env KEYSTORE_PASS \
        -keypass:env KEYSTORE_PASS
    echo "==> generated the release key (RSA 4096, alias $slug)"

    # --- 3. pass: entries end in a newline, as pass expects ------------------
    { base64 -w0 "$keystore"; echo; } | pass_put RELEASE_KEYSTORE_BASE64
    printf '%s\n' "$password" | pass_put RELEASE_KEYSTORE_PASSWORD
    printf '%s\n' "$password" | pass_put RELEASE_KEY_PASSWORD
    printf '%s\n' "$slug" | pass_put RELEASE_KEY_ALIAS
    echo "==> stored ${names[*]} under $entry_dir in pass"

    # --- 4. GitHub: values without a trailing newline -------------------------
    if ! $dry_run; then
        gh api -X PUT "repos/$repo/environments/$env_name" >/dev/null
    fi
    base64 -w0 "$keystore" | gh_set RELEASE_KEYSTORE_BASE64
    printf '%s' "$password" | gh_set RELEASE_KEYSTORE_PASSWORD
    printf '%s' "$password" | gh_set RELEASE_KEY_PASSWORD
    printf '%s' "$slug" | gh_set RELEASE_KEY_ALIAS
    $dry_run || echo "==> set ${names[*]} in the $env_name environment on $repo"

    # --- 5. fingerprint ----------------------------------------------------------
    # The certificate's SHA-256 fingerprint is this key's stable public identity.
    fingerprint=$(
        KEYSTORE_PASS="$password" "$keytool" -list -v \
            -keystore "$keystore" \
            -storepass:env KEYSTORE_PASS \
            -alias "$slug" 2>/dev/null |
            sed -nE 's/^[[:space:]]*SHA256:[[:space:]]*([0-9A-Fa-f:]+)$/\1/p' || true
    )
    if [ -n "$fingerprint" ]; then
        echo "==> SHA-256 fingerprint: $fingerprint"
    else
        echo "warning: could not read the certificate fingerprint" >&2
    fi
    unset password
fi

# --- RELEASE_PLEASE_TOKEN -------------------------------------------------------
if $with_token || $token_only; then
    if [ -e "$store_dir/$entry_dir/RELEASE_PLEASE_TOKEN.gpg" ]; then
        pass show "$entry_dir/RELEASE_PLEASE_TOKEN" | head -n1 | tr -d '\n' | gh_set RELEASE_PLEASE_TOKEN
        $dry_run || echo "==> set RELEASE_PLEASE_TOKEN in the $env_name environment on $repo"
    else
        echo "error: no pass entry $entry_dir/RELEASE_PLEASE_TOKEN; create the token first (see the header of this script)" >&2
        exit 1
    fi
else
    echo "==> RELEASE_PLEASE_TOKEN not set. Without it the release PR is opened with"
    echo "    GITHUB_TOKEN and CI doesn't run on it. To add it later, see the header of"
    echo "    this script, then rerun with --release-please-token-only."
fi

if ! $dry_run && ! $token_only; then
    echo "==> back the key up off this machine: pass git push"
fi
