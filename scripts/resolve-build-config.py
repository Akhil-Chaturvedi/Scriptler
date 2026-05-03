#!/usr/bin/env python3
"""
resolve-build-config.py — Automatic Chaquopy + Python version resolver for Scriptler.

Reads prebundled_packages.txt, queries the Chaquopy PyPI repository,
and selects the best Chaquopy version + Python version combination
that supports all declared packages for all configured ABIs.

Outputs:
  - app/build-config.properties (Python version for Gradle to read)
  - Optionally modifies gradle/libs.versions.toml (if Chaquopy downgrade needed)

Usage:
  py scripts/resolve-build-config.py
"""

import argparse
import os
import re
import sys
from urllib.request import urlopen, Request
from urllib.error import HTTPError, URLError


# ─── Constants ────────────────────────────────────────────────────────────────

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)
PACKAGES_FILE = os.path.join(PROJECT_DIR, "prebundled_packages.txt")
TOML_FILE = os.path.join(PROJECT_DIR, "gradle", "libs.versions.toml")
BUILD_CONFIG_FILE = os.path.join(PROJECT_DIR, "app", "build-config.properties")
PYPI_REPO_URL = "https://chaquo.com/pypi-13.1"
DEFAULT_PYTHON_VERSION = "3.10"

# Default ABIs (overridden by '// ABIs:' line in prebundled_packages.txt)
DEFAULT_ABIS = ["armeabi-v7a", "arm64-v8a"]
VALID_ABIS = {"armeabi-v7a", "arm64-v8a", "x86_64", "x86"}
ABIS = list(DEFAULT_ABIS)  # Updated by main() from packages file

# Chaquopy version support matrix (verified 2026-05-03):
#   (chaquopy_version, (min_agp), (max_agp), [python_versions highest-first])
#   AGP and Chaquopy versions are parsed as tuples for comparison.
CHAQUOPY_MATRIX = [
    ("17.0.0", (7, 3, 0), (9, 2, 0), ["3.14", "3.13", "3.12", "3.11", "3.10"]),
    # 16.1 skipped — same Python range as 17.0
    ("16.0.0", (7, 0, 0), (8, 8, 0), ["3.13", "3.12", "3.11", "3.10", "3.9", "3.8"]),
    ("15.0.0", (7, 0, 0), (8, 5, 0), ["3.12", "3.11", "3.10", "3.9", "3.8"]),
]


# ─── Helper Functions ─────────────────────────────────────────────────────────


def read_packages(path):
    """Read prebundled_packages.txt, skip comments (// or #) and empty lines.

    Returns list of package requirement strings (e.g. "torch>=1.7.0").
    """
    packages = []
    if not os.path.exists(path):
        return packages
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith("#") or stripped.startswith("//"):
                continue
            packages.append(stripped)
    return packages


def read_abis(path):
    """Read ABI configuration from prebundled_packages.txt.

    Looks for a line starting with '// ABIs:' and parses the comma-separated
    ABI list. Returns default ABIs if the line is not found.

    Valid ABIs: arm64-v8a, armeabi-v7a
    """
    if not os.path.exists(path):
        return list(DEFAULT_ABIS)

    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            stripped = line.strip()
            if stripped.startswith("// ABIs:"):
                abi_str = stripped[len("// ABIs:"):].strip()
                abis = [a.strip() for a in abi_str.split(",") if a.strip()]
                if not abis:
                    return list(DEFAULT_ABIS)
                # Validate
                for abi in abis:
                    if abi not in VALID_ABIS:
                        print(f"ERROR: Unknown ABI '{abi}' in prebundled_packages.txt")
                        print(f"  Valid ABIs: {', '.join(sorted(VALID_ABIS))}")
                        sys.exit(1)
                return abis

    return list(DEFAULT_ABIS)


def extract_package_name(requirement):
    """Extract normalized package name from a PEP 508 requirement string.

    Splits on version specifiers (><=!~), whitespace, semicolons, and brackets.
    Converts to lowercase and replaces underscores with hyphens.

    Example: "torch>=1.7.0" → "torch"
    """
    name = re.split(r"[><=!~;\s\[]", requirement)[0]
    return name.strip().lower().replace("_", "-")


def read_toml_version(path, key):
    """Read a `key = "value"` version string from a TOML file using regex.

    Returns the version string, or None if not found.
    """
    if not os.path.exists(path):
        return None
    pattern = re.compile(r"^" + re.escape(key) + r'\s*=\s*"([^"]+)"')
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            m = pattern.match(line)
            if m:
                return m.group(1)
    return None


def parse_version(version_str):
    """Parse a version string like "17.0.0" into a tuple of ints.

    Pads to at least 3 components so (8, 5) becomes (8, 5, 0).
    """
    parts = [int(p) for p in version_str.split(".")]
    while len(parts) < 3:
        parts.append(0)
    return tuple(parts)


def versions_equal(v1, v2):
    """Check if two version strings are semantically equivalent.

    Handles different precision: "17.0" == "17.0.0".
    """
    return parse_version(v1) == parse_version(v2)


def get_agp_compatible_chaquopy_versions(agp_version):
    """Return Chaquopy versions compatible with the given AGP version.

    Skips versions with duplicate Python ranges. Returns highest first.
    Each entry is (chaquopy_version_str, python_versions_list).
    """
    agp_tuple = parse_version(agp_version)
    seen_python_ranges = set()
    result = []

    for chaq_ver, min_agp, max_agp, py_versions in CHAQUOPY_MATRIX:
        if not (min_agp <= agp_tuple <= max_agp):
            continue

        py_range_key = tuple(py_versions)
        if py_range_key in seen_python_ranges:
            continue  # Skip duplicate Python range (e.g. 16.1 same as 17.0)
        seen_python_ranges.add(py_range_key)

        result.append((chaq_ver, py_versions))

    return result


def get_supported_python_versions(chaquopy_version):
    """Return Python versions supported by a Chaquopy version. Highest first."""
    for chaq_ver, _, _, py_versions in CHAQUOPY_MATRIX:
        if chaq_ver == chaquopy_version:
            return py_versions
    return []


def fetch_wheels(repo_url, package_name):
    """Fetch wheel filenames from the Chaquopy PyPI index for a package.

    Returns a list of wheel filenames, or None if the package was not found (404).
    Exits with an error message on network failures.
    """
    url = f"{repo_url}/{package_name}/"
    try:
        req = Request(url, headers={"User-Agent": "Scriptler-Build-Resolver/1.0"})
        with urlopen(req, timeout=30) as response:
            html = response.read().decode("utf-8")
    except HTTPError as e:
        if e.code == 404:
            return None  # Package not in Chaquopy repo — assume pure Python
        print(f"ERROR: HTTP {e.code} fetching {url}: {e.reason}")
        sys.exit(1)
    except URLError as e:
        print(f"ERROR: Network error fetching {url}: {e.reason}")
        sys.exit(1)
    except Exception as e:
        print(f"ERROR: Failed to fetch {url}: {e}")
        sys.exit(1)

    return re.findall(r'href="([^"]+\.whl)"', html)


def parse_wheel_tags(filename):
    """Parse wheel filename into python_tag, abi_tag, platform_tag.

    Reads from the end to handle optional build numbers.
    Returns dict with keys 'python_tag', 'abi_tag', 'platform_tag', or None.
    """
    name = filename[:-4] if filename.endswith(".whl") else filename
    parts = name.split("-")
    if len(parts) < 3:
        return None

    return {
        "python_tag": parts[-3],
        "abi_tag": parts[-2],
        "platform_tag": parts[-1],
    }


def is_python_compatible(python_tag, target_version):
    """Check if a wheel's python_tag is compatible with a target Python version.

    Matches: exact cpXY tag, 'py3' tag, or tags containing 'py3' (e.g. 'py2.py3').
    """
    target_tag = "cp" + target_version.replace(".", "")
    if python_tag == target_tag:
        return True
    if python_tag == "py3":
        return True
    if "py3" in python_tag:
        return True
    return False


def is_platform_compatible(platform_tag, abi):
    """Check if a wheel's platform_tag matches the target ABI.

    Normalizes ABI by replacing hyphens with underscores, then checks
    if the platform_tag contains the normalized ABI string.
    """
    normalized_abi = abi.replace("-", "_")
    return normalized_abi in platform_tag


def check_package_compatible(wheels, python_version, abis):
    """Check if a package has compatible wheels for ALL given ABIs.

    For each ABI, at least one wheel must have a compatible python_tag
    AND a matching platform_tag. Returns True only if every ABI is covered.
    """
    for abi in abis:
        abi_compatible = False
        for wheel in wheels:
            tags = parse_wheel_tags(wheel)
            if tags is None:
                continue
            if (is_python_compatible(tags["python_tag"], python_version)
                    and is_platform_compatible(tags["platform_tag"], abi)):
                abi_compatible = True
                break
        if not abi_compatible:
            return False
    return True


def update_toml_chaquopy(path, new_version):
    """Update the chaquopy version in libs.versions.toml.

    Uses line-by-line processing to safely modify only the target line.
    Preserves all other lines exactly as they are, including line endings.
    """
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    # Pattern matches ONLY the version assignment: chaquopy = "X.Y.Z"
    # Does NOT match the plugins line: chaquopy = { id = "...", version.ref = "..." }
    chaquopy_version_pattern = re.compile(r'^chaquopy\s*=\s*"([^"]+)"\s*$')
    guard_prefix = "# AUTO-MODIFIED by resolve-build-config.py"

    new_lines = []
    found = False
    i = 0
    while i < len(lines):
        line = lines[i]

        # Skip any existing AUTO-MODIFIED guard comment
        if line.strip().startswith(guard_prefix):
            i += 1
            continue

        # Check if this is the chaquopy version line
        match = chaquopy_version_pattern.match(line)
        if match and not found:
            old_version = match.group(1)
            if not versions_equal(old_version, new_version):
                # Add guard comment then the new version line
                new_lines.append(
                    f"# AUTO-MODIFIED by resolve-build-config.py -- original: {old_version}\n"
                )
                new_lines.append(f'chaquopy = "{new_version}"\n')
            else:
                # Already correct, keep as-is (no guard comment needed)
                new_lines.append(line)
            found = True
        else:
            new_lines.append(line)

        i += 1

    with open(path, "w", encoding="utf-8") as f:
        f.writelines(new_lines)


def write_build_config(python_version, chaquopy_version, original_chaquopy,
                       compatible_packages=None, abis=None):
    """Write app/build-config.properties with the resolved versions.

    Args:
        python_version: Resolved Python version string (e.g. "3.10").
        chaquopy_version: Resolved Chaquopy version string (e.g. "16.0.0").
        original_chaquopy: The original Chaquopy version before any downgrade.
        compatible_packages: Optional list of compatible package requirement strings.
            If provided, written as comma-separated compatiblePackages property.
            If None, the property is omitted (auto-resolve mode).
        abis: List of ABI strings (e.g. ["arm64-v8a"]). Written as comma-separated.
    """
    content = (
        "# Auto-generated by resolve-build-config.py -- do not edit\n"
        f"pythonVersion={python_version}\n"
        f"chaquopyVersion={chaquopy_version}\n"
        f"originalChaquopyVersion={original_chaquopy}\n"
    )
    if compatible_packages is not None:
        content += f"compatiblePackages={','.join(compatible_packages)}\n"
    if abis is not None:
        content += f"abis={','.join(abis)}\n"
    os.makedirs(os.path.dirname(BUILD_CONFIG_FILE), exist_ok=True)
    with open(BUILD_CONFIG_FILE, "w", encoding="utf-8") as f:
        f.write(content)


def read_original_chaquopy_from_config():
    """Read originalChaquopyVersion from build-config.properties if it exists.

    This handles the case where the resolver is run multiple times — the
    original version is preserved across runs.
    """
    if not os.path.exists(BUILD_CONFIG_FILE):
        return None
    try:
        with open(BUILD_CONFIG_FILE, "r", encoding="utf-8") as f:
            for line in f:
                stripped = line.strip()
                if stripped.startswith("originalChaquopyVersion="):
                    return stripped.split("=", 1)[1]
    except Exception:
        pass
    return None


# ─── Argument Parsing ────────────────────────────────────────────────────────


def parse_args():
    """Parse command-line arguments for pinned version flags."""
    parser = argparse.ArgumentParser(
        description="Resolve Chaquopy + Python versions for Scriptler build"
    )
    parser.add_argument(
        "--chaquopy", metavar="VERSION",
        help="Pin Chaquopy version (e.g. 16.0.0). Auto-picks best Python."
    )
    parser.add_argument(
        "--python", metavar="VERSION",
        help="Pin Python version (e.g. 3.10). Auto-picks best Chaquopy."
    )
    return parser.parse_args()


# ─── Pinned Version Mode ─────────────────────────────────────────────────────


def _validate_chaquopy_in_matrix(pinned_chaquopy, agp_version):
    """Validate a pinned Chaquopy version against the matrix and AGP compatibility.

    Returns (chaquopy_ver, python_versions) if valid, exits with error otherwise.
    """
    agp_tuple = parse_version(agp_version)

    for chaq_ver, min_agp, max_agp, py_versions in CHAQUOPY_MATRIX:
        if chaq_ver == pinned_chaquopy:
            if not (min_agp <= agp_tuple <= max_agp):
                min_str = ".".join(str(x) for x in min_agp)
                max_str = ".".join(str(x) for x in max_agp)
                print(f"ERROR: Pinned Chaquopy {pinned_chaquopy} is not compatible with AGP {agp_version}")
                print(f"  AGP range for Chaquopy {pinned_chaquopy}: {min_str} - {max_str}")
                sys.exit(1)
            return chaq_ver, py_versions

    available = ", ".join(cv for cv, _, _, _ in CHAQUOPY_MATRIX)
    print(f"ERROR: Unknown Chaquopy version: {pinned_chaquopy}")
    print(f"  Available versions: {available}")
    sys.exit(1)


def _find_chaquopy_for_python(pinned_python, agp_version):
    """Find AGP-compatible Chaquopy versions that support the pinned Python version.

    Returns list of (chaquopy_ver, python_versions) highest first.
    Exits with error if none found.
    """
    agp_tuple = parse_version(agp_version)
    result = []

    for chaq_ver, min_agp, max_agp, py_versions in CHAQUOPY_MATRIX:
        if not (min_agp <= agp_tuple <= max_agp):
            continue
        if pinned_python in py_versions:
            result.append((chaq_ver, py_versions))

    if not result:
        compatible_chaq = get_agp_compatible_chaquopy_versions(agp_version)
        if compatible_chaq:
            all_py = set()
            for _, pvs in compatible_chaq:
                all_py.update(pvs)
            sorted_py = sorted(all_py, key=lambda v: parse_version(v), reverse=True)
            print(f"ERROR: Python {pinned_python} is not supported by any AGP-compatible Chaquopy version")
            print(f"  Available Python versions: {', '.join(sorted_py)}")
        else:
            print(f"ERROR: No Chaquopy version is compatible with AGP {agp_version}")
        sys.exit(1)

    return result


def _classify_packages(packages, package_names, wheels_cache, python_ver):
    """Classify packages into compatible and incompatible lists.

    Returns (compatible_requirements, incompatible_pairs) where
    incompatible_pairs is list of (requirement_str, package_name).
    """
    compatible = []
    incompatible = []

    for i, pkg_name in enumerate(package_names):
        wheels = wheels_cache[pkg_name]
        if wheels is None:
            compatible.append(packages[i])  # Pure Python -- always compatible
            continue
        if check_package_compatible(wheels, python_ver, ABIS):
            compatible.append(packages[i])
        else:
            incompatible.append((packages[i], pkg_name))

    return compatible, incompatible


def _apply_chaquopy_version(chaquopy_ver, original_chaquopy):
    """Update TOML if needed and print warnings for Chaquopy changes."""
    current_toml_chaquopy = read_toml_version(TOML_FILE, "chaquopy")
    if not versions_equal(current_toml_chaquopy, chaquopy_ver):
        update_toml_chaquopy(TOML_FILE, chaquopy_ver)
        if not versions_equal(chaquopy_ver, original_chaquopy):
            print(
                f"\nWARNING: Chaquopy changed from {original_chaquopy} to {chaquopy_ver}."
            )
            print(
                "gradle/libs.versions.toml has been modified. "
                "Run 'git checkout gradle/libs.versions.toml' to revert."
            )


def _print_python_warning(python_ver):
    """Print warning if Python version is below 3.10."""
    if parse_version(python_ver) < (3, 10, 0):
        print(
            f"\nWARNING: Python version downgraded from 3.10 to {python_ver}."
        )
        print("Python code using 3.10+ features (match/case, union types) may not work.")


def resolve_pinned_mode(pinned_chaquopy, pinned_python, packages, package_names,
                        wheels_cache, agp_version, original_chaquopy):
    """Resolve versions when --chaquopy and/or --python flags are set.

    In pinned mode, incompatible packages are SKIPPED with warnings.
    The build continues with only the compatible subset.
    """
    print("\nMode: PINNED VERSIONS")

    # ── Step 1: Determine Chaquopy version ──
    if pinned_chaquopy:
        chaquopy_ver, available_py = _validate_chaquopy_in_matrix(
            pinned_chaquopy, agp_version
        )
        print(f"Pinned Chaquopy: {chaquopy_ver}")
    else:
        # --python only: find Chaquopy versions supporting this Python
        chaquopy_candidates = _find_chaquopy_for_python(pinned_python, agp_version)
        # Use highest compatible Chaquopy version
        chaquopy_ver = chaquopy_candidates[0][0]
        available_py = chaquopy_candidates[0][1]
        print(f"Auto-selected Chaquopy: {chaquopy_ver} (supports Python {pinned_python})")

    # ── Step 2: Determine Python version ──
    if pinned_python:
        if pinned_python not in available_py:
            print(f"ERROR: Python {pinned_python} is not supported by Chaquopy {chaquopy_ver}")
            print(f"  Supported Python versions: {', '.join(available_py)}")
            sys.exit(1)
        python_ver = pinned_python
        print(f"Pinned Python: {python_ver}")
    else:
        # --chaquopy only: auto-pick best Python (highest where all packages work,
        # or highest with most compatible packages)
        best_python = None
        best_compatible_count = -1

        for pv in available_py:
            compatible, _ = _classify_packages(
                packages, package_names, wheels_cache, pv
            )
            count = len(compatible)
            if count > best_compatible_count:
                best_compatible_count = count
                best_python = pv
            if count == len(packages):
                break  # All packages compatible -- no need to try lower

        python_ver = best_python
        print(f"Auto-selected Python: {python_ver}")

    # ── Step 3: Classify packages ──
    compatible, incompatible = _classify_packages(
        packages, package_names, wheels_cache, python_ver
    )

    if incompatible:
        print(f"\nWARNING: {len(incompatible)} package(s) skipped (incompatible with pinned versions):")
        for req_str, pkg_name in incompatible:
            print(f"  - {req_str}: no compatible wheels for Python {python_ver} on all ABIs")

    if compatible:
        print(f"\nCompatible packages ({len(compatible)}): {', '.join(compatible)}")
    else:
        print("\nWARNING: No packages are compatible with the pinned versions.")

    # ── Step 4: Write config and update TOML ──
    print(f"\n>> Resolved: Chaquopy {chaquopy_ver}, Python {python_ver}")

    write_build_config(python_ver, chaquopy_ver, original_chaquopy,
                       compatible_packages=compatible, abis=ABIS)
    _apply_chaquopy_version(chaquopy_ver, original_chaquopy)
    _print_python_warning(python_ver)


# ─── Main ─────────────────────────────────────────────────────────────────────


def main():
    """Orchestrate the Chaquopy + Python version resolution."""

    global ABIS

    args = parse_args()
    pinned_mode = args.chaquopy is not None or args.python is not None

    # 1. Read ABIs from packages file
    ABIS = read_abis(PACKAGES_FILE)
    print(f"Target ABIs: {', '.join(ABIS)}")

    # 2. Read packages
    packages = read_packages(PACKAGES_FILE)

    if not packages:
        # No packages -- use defaults
        toml_chaquopy = read_toml_version(TOML_FILE, "chaquopy") or "17.0.0"
        original = read_original_chaquopy_from_config() or toml_chaquopy
        write_build_config(DEFAULT_PYTHON_VERSION, toml_chaquopy, original, abis=ABIS)
        print(f"No packages declared. Using defaults: Chaquopy {toml_chaquopy}, Python {DEFAULT_PYTHON_VERSION}")
        return

    # 2. Read current versions from TOML
    agp_version = read_toml_version(TOML_FILE, "agp")
    if not agp_version:
        print("ERROR: Could not read AGP version from gradle/libs.versions.toml")
        sys.exit(1)

    toml_chaquopy = read_toml_version(TOML_FILE, "chaquopy")
    if not toml_chaquopy:
        print("ERROR: Could not read Chaquopy version from gradle/libs.versions.toml")
        sys.exit(1)

    # Use original Chaquopy version if resolver was run before
    original_chaquopy = read_original_chaquopy_from_config() or toml_chaquopy

    print(f"Declared packages: {', '.join(packages)}")
    print(f"AGP version: {agp_version}")
    print(f"Chaquopy version (original): {original_chaquopy}")

    # 3. Extract package names and fetch wheels ONCE (ABIS already set above)
    package_names = [extract_package_name(pkg) for pkg in packages]
    wheels_cache = {}

    for pkg_name in package_names:
        print(f"Fetching wheel data for '{pkg_name}'...")
        wheels = fetch_wheels(PYPI_REPO_URL, pkg_name)
        if wheels is None:
            print(f"  INFO: '{pkg_name}' not found in Chaquopy repo -- assuming pure Python")
        else:
            print(f"  Found {len(wheels)} wheels")
        wheels_cache[pkg_name] = wheels

    # 4. Branch: pinned mode vs auto-resolve mode
    if pinned_mode:
        resolve_pinned_mode(
            args.chaquopy, args.python, packages, package_names,
            wheels_cache, agp_version, original_chaquopy
        )
        return

    # ── Auto-resolve mode (no flags) ──
    # 5. Get AGP-compatible Chaquopy versions
    compatible_versions = get_agp_compatible_chaquopy_versions(agp_version)
    if not compatible_versions:
        print(f"ERROR: No Chaquopy version is compatible with AGP {agp_version}")
        sys.exit(1)

    print(f"Compatible Chaquopy versions to try: {', '.join(v[0] for v in compatible_versions)}")

    # 6. Try each Chaquopy version (highest to lowest), each Python version (highest to lowest)
    for chaquopy_ver, python_versions in compatible_versions:
        for python_ver in python_versions:
            all_compatible = True

            for pkg_name in package_names:
                wheels = wheels_cache[pkg_name]
                if wheels is None:
                    continue  # Pure Python -- no constraint

                if not check_package_compatible(wheels, python_ver, ABIS):
                    all_compatible = False
                    break

            if all_compatible:
                # Found a compatible combination!
                print(f"\n>> Resolved: Chaquopy {chaquopy_ver}, Python {python_ver}")

                # Write build config (no compatiblePackages filter in auto-resolve mode)
                write_build_config(python_ver, chaquopy_ver, original_chaquopy, abis=ABIS)

                # Update TOML if Chaquopy version changed from current TOML value
                _apply_chaquopy_version(chaquopy_ver, original_chaquopy)
                _print_python_warning(python_ver)

                return

    # 7. No combination found -- fail with actionable error
    print("\nERROR: No compatible Chaquopy + Python version combination found for:")

    for pkg_name in package_names:
        if wheels_cache[pkg_name] is None:
            print(f"  - {pkg_name}: not in Chaquopy repo (pure Python, no constraint)")
        else:
            print(
                f"  - {pkg_name}: no compatible wheels for any Python version "
                f"in any compatible Chaquopy version"
            )

    print("\nTried combinations:")
    for chaquopy_ver, python_versions in compatible_versions:
        py_min = python_versions[-1]
        py_max = python_versions[0]

        failed = []
        for pkg_name in package_names:
            if wheels_cache[pkg_name] is None:
                continue
            any_works = any(
                check_package_compatible(wheels_cache[pkg_name], pv, ABIS)
                for pv in python_versions
            )
            if not any_works:
                failed.append(pkg_name)

        if failed:
            print(
                f"  - Chaquopy {chaquopy_ver} (Python {py_min}-{py_max}): "
                f"{', '.join(failed)} has no compatible wheels"
            )
        else:
            print(
                f"  - Chaquopy {chaquopy_ver} (Python {py_min}-{py_max}): "
                f"all packages have compatible wheels"
            )

    print("\nOptions:")
    print("  1. Remove incompatible packages from prebundled_packages.txt")
    print("  2. Use --chaquopy or --python flags to pin specific versions")
    print(f"  3. Check if the package exists at {PYPI_REPO_URL}/")

    sys.exit(1)


if __name__ == "__main__":
    main()
