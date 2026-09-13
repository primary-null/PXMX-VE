#!/usr/bin/env python3
"""
PXMX-VE Security & Anonymity Enforcement Checker.
Protects against leaking PII, local usernames, host paths, device serials,
unapproved documents, and secrets into the Git repository.

Usage:
    python scripts/security_check.py --staged      (pre-commit)
    python scripts/security_check.py --pre-push   (pre-push)
    python scripts/security_check.py --all        (CI audit)
"""

import sys
import os
import re
import base64
import subprocess
import argparse

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

if sys.stderr and hasattr(sys.stderr, "reconfigure"):
    try:
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

# -----------------------------------------------------------------------------
# Configuration & Blacklists
# -----------------------------------------------------------------------------

# Helper: obfuscate target tokens so this security script itself does not contain plain text secrets
def _b64(val: str) -> str:
    try:
        return base64.b64decode(val.encode("ascii")).decode("utf-8")
    except Exception:
        return ""

# Specific hardware & lab identifiers (stored as base64 to avoid plain text in repo)
_S_TOKENS = [
    (_b64("UkVEQUNURUQ="), "Local Windows dev username pattern"),
    (_b64("UkVEQUNURUQ="), "Hardware Fold3 device serial"),
    (_b64("UkVEQUNURUQ="), "Hardware Pixel 8 Pro device serial"),
    (_b64("UkVEQUNURUQ="), "Hardware A13 device serial"),
    (_b64("UkVEQUNURUQ="), "Real cluster host IP"),
    (_b64("UkVEQUNURUQ="), "Real cluster host IP"),
]

# Prohibited string patterns (regex)
FORBIDDEN_CONTENT_PATTERNS = [
    # Generic Windows / Unix user paths
    (r"[cC]:[\\/]+[uU]sers[\\/]+[a-zA-Z0-9_\-\.]+", "Absolute Windows user directory path detected"),
    (r"/(home|Users)/[a-zA-Z0-9_\-\.]+", "Absolute Unix user directory path detected"),
    (r"\bAppData[\\/]+Local\b", "Windows AppData path detected"),
    (r"\bWinSta0[\\/]+", "Windows WindowStation reference detected"),

    # Windows AppData / WindowStation paths
    (r"\bAppData[\\/]+Local\b", "Windows AppData path detected"),
    (r"\bWinSta0[\\/]+", "Windows WindowStation reference detected"),


    # Secrets and private tokens
    (r"-----BEGIN (RSA|EC|OPENSSH|PGP|PRIVATE) KEY-----", "Private cryptographic key block detected"),
    (r"(PVEAPIToken|api_token)\s*[:=]\s*['\"][a-zA-Z0-9_\-=+]{20,}['\"]", "Live Proxmox API token detected"),
    (r"PVEAuthCookie=[a-zA-Z0-9_\-=+]{20,}", "Live Proxmox authentication ticket detected"),
]

# Prohibited file patterns and extensions
FORBIDDEN_FILE_EXTENSIONS = {
    ".bat", ".ps1", ".html", ".pdf", ".exe", ".bin",
    ".apk", ".keystore", ".jks", ".p12", ".pem", ".key",
}

# Whitelisted root scripts and public docs that are safe to exist in repo
ALLOWED_EXEMPT_FILES = {
    "gradlew.bat",
    "docs/index.html",
    "docs/showcase.html",
}

# File name prefixes/names that are strictly prohibited from being committed
FORBIDDEN_FILE_NAMES = [
    "AGENTS.md",
    "launch_",
    "PXMX_User_Manual",
    "manual",
    ".env",
]

# Allowed commit author patterns
ALLOWED_AUTHOR_NAMES = {"primary-null", "github-actions[bot]", "Primary Node"}
ALLOWED_AUTHOR_EMAILS = {
    "primary-null@users.noreply.github.com",
    "primary-null@users.noreply.github.com",
    "41898282+github-actions[bot]@users.noreply.github.com",
}

# -----------------------------------------------------------------------------
# Helper Functions
# -----------------------------------------------------------------------------

def run_git(args):
    """Run a git command and return stdout as string."""
    try:
        res = subprocess.run(["git"] + args, capture_output=True, text=True, encoding="utf-8", errors="replace", check=True)
        return res.stdout.strip() if res.stdout else ""
    except subprocess.CalledProcessError as e:
        print(f"[SECURITY ERROR] Git command failed: git {' '.join(args)}\n{e.stderr}", file=sys.stderr)
        return ""
    except Exception as e:
        print(f"[SECURITY ERROR] Git execution error: {e}", file=sys.stderr)
        return ""

def check_file_path(file_path):
    """Verify that file path does not violate file name/extension rules."""
    violations = []
    base_name = os.path.basename(file_path)
    _, ext = os.path.splitext(file_path)

    # Check extension
    normalized_path = file_path.replace("\\", "/")
    if ext.lower() in FORBIDDEN_FILE_EXTENSIONS:
        if base_name not in ALLOWED_EXEMPT_FILES and normalized_path not in ALLOWED_EXEMPT_FILES:
            violations.append(f"Forbidden file extension '{ext}' in file: {file_path}")

    # Check file name / prefix
    for forbidden in FORBIDDEN_FILE_NAMES:
        if forbidden.lower() in base_name.lower():
            violations.append(f"Forbidden file name pattern '{forbidden}' in file: {file_path}")

    return violations

def check_content(content, source_name):
    """Scan string content against forbidden regex patterns and environment usernames."""
    violations = []
    
    # Check current host username dynamically from environment (ignoring common CI runner names)
    SYSTEM_USERS_IGNORE = {"runner", "root", "admin", "ubuntu", "jenkins", "node", "git", "builder", "actions"}
    env_user = os.environ.get("USERNAME") or os.environ.get("USER")
    
    for line_no, line in enumerate(content.splitlines(), start=1):
        # 1. Check dynamic host username
        if env_user and env_user.lower() not in SYSTEM_USERS_IGNORE and len(env_user) > 2:
            if re.search(r"\b" + re.escape(env_user) + r"\b", line, re.IGNORECASE):
                violations.append(f"{source_name}:{line_no} -> Host system username '{env_user}' detected\n   Line: {line.strip()[:120]}")


        # 2. Check specific protected tokens
        for token, desc in _S_TOKENS:
            if token and token in line:
                violations.append(f"{source_name}:{line_no} -> {desc} detected\n   Line: {line.strip()[:120]}")

        # 3. Check regex patterns
        for pattern, description in FORBIDDEN_CONTENT_PATTERNS:
            if re.search(pattern, line, re.IGNORECASE):
                violations.append(f"{source_name}:{line_no} -> {description}\n   Line: {line.strip()[:120]}")
                
    return violations

# -----------------------------------------------------------------------------
# Check Modes
# -----------------------------------------------------------------------------

def check_staged():
    """Check currently staged files and their diffs (Pre-Commit)."""
    violations = []
    staged_files = run_git(["diff", "--cached", "--name-only", "--diff-filter=d"]).splitlines()

    for f in staged_files:
        f = f.strip()
        if not f:
            continue
        # Check path
        violations.extend(check_file_path(f))
        # Check staged diff content
        diff = run_git(["diff", "--cached", "-U0", "--", f])
        # Only scan added lines in diff
        added_lines = [line[1:] for line in diff.splitlines() if line.startswith("+") and not line.startswith("+++")]
        violations.extend(check_content("\n".join(added_lines), f))

    return violations

def check_commits(rev_range="origin/main..HEAD"):
    """Check outgoing commits (Pre-Push)."""
    violations = []
    commits = run_git(["rev-list", rev_range]).splitlines()
    if not commits:
        return []

    for commit in commits:
        commit = commit.strip()
        if not commit:
            continue

        # Check author & committer
        author_name = run_git(["show", "-s", "--format=%an", commit])
        author_email = run_git(["show", "-s", "--format=%ae", commit])

        if author_name not in ALLOWED_AUTHOR_NAMES:
            violations.append(f"Commit {commit[:7]} has unauthorized author name: '{author_name}' (expected Primary Node / primary-null)")

        if author_email not in ALLOWED_AUTHOR_EMAILS:
            violations.append(f"Commit {commit[:7]} has unauthorized author email: '{author_email}'")

        # Check changed files in commit
        files = run_git(["diff-tree", "--no-commit-id", "--name-only", "-r", commit]).splitlines()
        for f in files:
            f = f.strip()
            if not f:
                continue
            violations.extend(check_file_path(f))

        # Check diff content of commit
        diff = run_git(["show", "--format=", "-U0", commit])
        added_lines = [line[1:] for line in diff.splitlines() if line.startswith("+") and not line.startswith("+++")]
        violations.extend(check_content("\n".join(added_lines), f"Commit {commit[:7]}"))

    return violations

def check_all_tracked():
    """Scan all tracked files in the repo (CI full audit)."""
    violations = []
    tracked_files = run_git(["ls-files"]).splitlines()

    for f in tracked_files:
        f = f.strip()
        if not f or not os.path.isfile(f):
            continue
        violations.extend(check_file_path(f))
        try:
            with open(f, "r", encoding="utf-8", errors="ignore") as fp:
                violations.extend(check_content(fp.read(), f))
        except Exception as e:
            violations.append(f"Could not read {f}: {e}")

    return violations

# -----------------------------------------------------------------------------
# Main Entrypoint
# -----------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="PXMX-VE Security & Anonymity Validator")
    parser.add_argument("--staged", action="store_true", help="Check staged files (pre-commit)")
    parser.add_argument("--pre-push", action="store_true", help="Check outgoing commits (pre-push)")
    parser.add_argument("--all", action="store_true", help="Check all tracked repository files (CI audit)")
    parser.add_argument("--range", type=str, default="origin/main..HEAD", help="Commit range for pre-push")

    args = parser.parse_args()

    if not (args.staged or args.pre_push or args.all):
        args.staged = True

    print("=" * 60)
    print("[SECURITY] PXMX-VE Security & Anonymity Enforcement Checker")
    print("=" * 60)

    violations = []
    if args.staged:
        print("[SCAN] Checking staged git index...")
        violations.extend(check_staged())

    if args.pre_push:
        print(f"[SCAN] Checking outgoing commits in range ({args.range})...")
        violations.extend(check_commits(args.range))

    if args.all:
        print("[SCAN] Running full repository audit...")
        violations.extend(check_all_tracked())

    if violations:
        print("\n[SECURITY ALERT] VIOLATIONS DETECTED! OPERATION ABORTED.")
        print("-" * 60)
        for v in violations:
            print(f" * {v}")
        print("-" * 60)
        print("Please resolve all violations before committing or pushing.\n")
        sys.exit(1)
    else:
        print("\n[SUCCESS] All security & anonymity checks PASSED. Zero leaks detected.")
        sys.exit(0)

if __name__ == "__main__":
    main()
