#!/usr/bin/env python3
"""Host-only validator for eval/fixtures-v4.json.

Run from the repository root as:  python eval/validate.py

Standard library only, no network, no emulator, no model inference, and no
mutation of any file: everything here is a read-only cross-check between the
fixture manifest, the pinned root lock files, and the application sources.

Exit code 0 when clean; exit code 1 with a numbered problem list otherwise.
Warnings (declared enums unused by any fixture) never affect the exit code.

What this validator proves and does not prove is stated in the manifest's
"authority" object and in eval/README.md. A passing run establishes only the
internal consistency of the declared evaluation artifacts.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MANIFEST_PATH = ROOT / "eval" / "fixtures-v4.json"
LIBRARY_PATH = ROOT / "app" / "src" / "androidTest" / "assets" / "library.json"
MODEL_LOCK_PATH = ROOT / "model-lock.json"
BONSAI_LOCK_PATH = ROOT / "bonsai-lock.json"
JUDGE_LOCK_PATH = ROOT / "judge-lock.json"
LLAMA_REVISION_PATH = ROOT / "llama-revision.txt"
GRADLE_PATH = ROOT / "app" / "build.gradle"
ANDROID_MANIFEST_PATH = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"

KNOWN_SCHEMA_VERSIONS = {"1", "2"}

# Fixed rubric dimension ids from docs/evaluation.md (binding for the manifest).
RUBRIC_DIMENSIONS = {
    "task-completion",
    "evidence-applicability",
    "supported-claims",
    "citation-resolution",
    "context-handling",
    "deterministic-result-correctness",
    "unknown-handling",
    "time-to-useful-information",
}

REQUIRED_TOP_LEVEL_KEYS = [
    "manifest_schema_version",
    "manifest_id",
    "manifest_version",
    "created",
    "status",
    "authority",
    "identity",
    "harness_prompt_wrapping",
    "question_families",
    "evidence_conditions",
    "evidence_layers",
    "authority_levels",
    "bounty_requirements",
    "capability_blockers",
    "fixtures",
    "holdout_policy",
]

FIXTURE_ID_PATTERN = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+$")

FIXTURE_REQUIRED_KEYS = [
    "id",
    "tier",
    "domain",
    "language",
    "question_families",
    "evidence_conditions",
    "evidence_layers",
    "clarification_required",
    "holdout",
    "requires_tools",
    "defined_in",
    "harness",
    "also_run_by",
    "import_payloads",
    "review_only",
    "notes",
    "initial_context",
    "turns",
    "expected_evidence",
    "successful_outcome",
    "critical_failures",
    "executable_checks",
    "review_dimensions",
    "observed_0_8",
    "bounty_requirement_ids",
    "blocked_by",
]

# Literal template/placeholder markers that must never appear in fixture text.
PLACEHOLDER_PATTERN = re.compile(r"<[^<>\n]*>")
FORBIDDEN_LITERALS = ("TODO", "FIXME")

VALID_TIERS = {"runnable", "blocked"}
VALID_SCOPE_VALUES = {"yes", "partial", "no", "informational"}

PROBLEMS = []
WARNINGS = []
MATRIX_LINES = []


def problem(message):
    PROBLEMS.append(message)


def warning(message):
    WARNINGS.append(message)


def load_json(path, label):
    if not path.is_file():
        problem("%s: required file is missing: %s" % (label, path))
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        problem("%s: could not be read as JSON: %s" % (label, exc))
        return None


def is_nonempty(value):
    if isinstance(value, (str, list, dict)):
        return len(value) > 0
    return value is not None


def check_01_parse_and_schema(manifest):
    """The manifest parses as JSON and manifest_schema_version is known."""
    if manifest is None:
        return
    version = manifest.get("manifest_schema_version")
    if version not in KNOWN_SCHEMA_VERSIONS:
        problem("check 1: manifest_schema_version %r is not a known value (known: %s)"
                % (version, ", ".join(sorted(KNOWN_SCHEMA_VERSIONS))))


def check_02_top_level_keys(manifest):
    """Every required top-level key is present and non-empty."""
    if manifest is None:
        return
    for key in REQUIRED_TOP_LEVEL_KEYS:
        if key not in manifest:
            problem("check 2: required top-level key is missing: %s" % key)
        elif not is_nonempty(manifest[key]):
            problem("check 2: required top-level key is empty: %s" % key)


def check_03_fixture_ids(manifest):
    """Fixture ids are unique and kebab-case with a -vN suffix."""
    if manifest is None:
        return
    seen = set()
    for fixture in manifest.get("fixtures", []):
        fixture_id = fixture.get("id", "<missing id>")
        if fixture_id in seen:
            problem("check 3: duplicate fixture id: %s" % fixture_id)
        seen.add(fixture_id)
        if not isinstance(fixture_id, str) or not FIXTURE_ID_PATTERN.match(fixture_id):
            problem("check 3: fixture id does not match ^[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+$: %r"
                    % (fixture_id,))


def check_04_fixture_keys(manifest):
    """Every fixture carries the full key set, with explicit null/[] where empty."""
    if manifest is None:
        return
    required = set(FIXTURE_REQUIRED_KEYS)
    if str(manifest.get("manifest_schema_version")) == "2":
        required.add("execution")
    for fixture in manifest.get("fixtures", []):
        fixture_id = fixture.get("id", "<missing id>")
        keys = set(fixture.keys())
        missing = sorted(required - keys)
        if missing:
            problem("check 4: fixture %s is missing keys (must be explicit null/[]): %s"
                    % (fixture_id, ", ".join(missing)))
        extra = sorted(keys - required)
        if extra:
            problem("check 4: fixture %s carries undeclared keys: %s"
                    % (fixture_id, ", ".join(extra)))


def check_05_enum_references(manifest):
    """All id references resolve: declared enums, rubric dimensions, bounty ids,
    question-family bounty ids, and requires_tools against capability_blockers
    roadmap ids. A runnable fixture must not require any tool."""
    if manifest is None:
        return
    family_ids = {f.get("id") for f in manifest.get("question_families", [])}
    condition_ids = {c.get("id") for c in manifest.get("evidence_conditions", [])}
    layer_ids = {l.get("id") for l in manifest.get("evidence_layers", [])}
    bounty_ids = {b.get("id") for b in manifest.get("bounty_requirements", [])}
    blocker_ids = {b.get("roadmap_id") for b in manifest.get("capability_blockers", [])}

    for family in manifest.get("question_families", []):
        family_id = family.get("id", "<missing id>")
        for bounty_id in family.get("bounty_requirement_ids") or []:
            if bounty_id not in bounty_ids:
                problem("check 5: question family %s references unknown bounty requirement id: %r"
                        % (family_id, bounty_id))

    enum_specs = [
        ("question_families", family_ids),
        ("evidence_conditions", condition_ids),
        ("evidence_layers", layer_ids),
        ("review_dimensions", RUBRIC_DIMENSIONS),
        ("bounty_requirement_ids", bounty_ids),
    ]

    for fixture in manifest.get("fixtures", []):
        fixture_id = fixture.get("id", "<missing id>")
        for field, allowed in enum_specs:
            for value in fixture.get(field) or []:
                if value not in allowed:
                    problem("check 5: fixture %s has unknown %s value: %r"
                            % (fixture_id, field, value))
        for tool_id in fixture.get("requires_tools") or []:
            if tool_id not in blocker_ids:
                problem("check 5: fixture %s requires undeclared tool/capability blocker: %r"
                        % (fixture_id, tool_id))
        if fixture.get("tier") == "runnable" and (fixture.get("requires_tools") or []):
            problem("check 5: runnable fixture %s must not require tools; requires_tools must "
                    "be empty so a runnable fixture can never depend on an unimplemented "
                    "capability: %s" % (fixture_id, ", ".join(fixture.get("requires_tools"))))


def check_06_tier_and_blockers(manifest):
    """tier blocked implies non-empty declared blocked_by; runnable implies none."""
    if manifest is None:
        return
    blocker_ids = {b.get("roadmap_id") for b in manifest.get("capability_blockers", [])}
    for fixture in manifest.get("fixtures", []):
        fixture_id = fixture.get("id", "<missing id>")
        tier = fixture.get("tier")
        blocked_by = fixture.get("blocked_by") or []
        if tier not in VALID_TIERS:
            problem("check 6: fixture %s has unknown tier: %r" % (fixture_id, tier))
            continue
        if tier == "blocked":
            if not blocked_by:
                problem("check 6: blocked fixture %s has an empty blocked_by" % fixture_id)
            for blocker in blocked_by:
                if blocker not in blocker_ids:
                    problem("check 6: fixture %s blocked by undeclared roadmap id: %r"
                            % (fixture_id, blocker))
        else:
            if blocked_by:
                problem("check 6: runnable fixture %s has a non-empty blocked_by: %s"
                        % (fixture_id, ", ".join(blocked_by)))


def check_07_deterministic_surface(manifest):
    """Every runnable fixture has a deterministic check, or is a holdout, or
    requires clarification, or is explicitly review_only. Every fixture with no
    deterministic check must carry non-empty review_dimensions and a notes
    entry saying it is judgement-only, so the review-only surface stays visible
    rather than hidden."""
    if manifest is None:
        return
    review_only = []
    for fixture in manifest.get("fixtures", []):
        fixture_id = fixture.get("id", "<missing id>")
        has_check = bool(fixture.get("executable_checks"))
        if fixture.get("tier") == "runnable" and not (
                has_check or fixture.get("holdout") is True
                or fixture.get("clarification_required") is True
                or fixture.get("review_only") is True):
            problem("check 7: runnable fixture %s has no executable check and is neither "
                    "holdout, clarification_required nor review_only" % fixture_id)
        if not has_check:
            review_only.append(fixture_id)
            if not fixture.get("review_dimensions"):
                problem("check 7: fixture %s has no executable check and must carry "
                        "non-empty review_dimensions" % fixture_id)
            notes = [n for n in (fixture.get("notes") or []) if isinstance(n, str)]
            if not any("judgement-only" in n for n in notes):
                problem("check 7: fixture %s has no executable check and must carry a notes "
                        "entry saying it is judgement-only" % fixture_id)
    MATRIX_LINES.append("fixtures with no deterministic executable check "
                        "(holdout/clarification/review-only): %s"
                        % (", ".join(review_only) if review_only else "none"))


def check_08_document_ids(manifest, library):
    """expected_evidence.document_ids, when non-empty, exist in library.json."""
    if manifest is None:
        return
    library_ids = None
    if isinstance(library, list):
        library_ids = {doc.get("id") for doc in library}
    elif isinstance(library, dict):
        docs = library.get("documents")
        if isinstance(docs, list):
            library_ids = {doc.get("id") for doc in docs}
    if library_ids is None:
        problem("check 8: library.json structure not recognised; cannot resolve document ids")
        return
    for fixture in manifest.get("fixtures", []):
        fixture_id = fixture.get("id", "<missing id>")
        expected = fixture.get("expected_evidence") or {}
        for document_id in expected.get("document_ids") or []:
            if document_id not in library_ids:
                problem("check 8: fixture %s references document id not in library.json: %r"
                        % (fixture_id, document_id))


def _lock_entries(model_lock, bonsai_lock, judge_lock):
    """Flatten the root lock files into a list of lock entry dicts. Each entry
    carries the repository and revision that the owning lock file declares for
    it: the entry's own values when present, otherwise the lock file's
    top-level values (model-lock.json and judge-lock.json declare repo and
    revision at the top level; their file entries do not)."""
    entries = []
    if isinstance(model_lock, dict) and model_lock.get("file"):
        entry = dict(model_lock)
        entry["_repo"] = model_lock.get("repo")
        entry["_revision"] = model_lock.get("revision")
        entry["_source"] = "model-lock.json"
        entries.append(entry)
    if isinstance(bonsai_lock, dict):
        for model in bonsai_lock.get("models") or []:
            if isinstance(model, dict) and model.get("file"):
                entry = dict(model)
                entry["_repo"] = model.get("repo") or bonsai_lock.get("repo")
                entry["_revision"] = model.get("revision") or bonsai_lock.get("revision")
                entry["_source"] = "bonsai-lock.json"
                entries.append(entry)
    if isinstance(judge_lock, dict):
        for file_entry in judge_lock.get("files") or []:
            if isinstance(file_entry, dict) and file_entry.get("file"):
                entry = dict(file_entry)
                # judge-lock.json file entries carry no repository or revision:
                # both are declared at the top level and bind to every file.
                entry["_repo"] = file_entry.get("repo") or judge_lock.get("repo")
                entry["_revision"] = file_entry.get("revision") or judge_lock.get("revision")
                entry["_source"] = "judge-lock.json"
                entries.append(entry)
    return [e for e in entries if isinstance(e, dict) and e.get("file")]


def check_09_identity_locks(manifest, model_lock, bonsai_lock, judge_lock, llama_revision_text):
    """identity.models hashes/sizes match the root lock files exactly; every
    profile's repo and revision match the owning lock file's own values (falling
    back to the lock file's top-level values when the per-file entry declares
    none, which is how judge-lock.json binds the Kev repository); the kev
    auxiliary files match judge-lock.json; llamaCppRevision matches
    llama-revision.txt."""
    if manifest is None:
        return
    identity = manifest.get("identity") or {}
    models = identity.get("models")
    if not isinstance(models, list) or not models:
        problem("check 9: identity.models must be a non-empty array of model profiles")
        models = []

    lock_entries = _lock_entries(model_lock, bonsai_lock, judge_lock)
    for profile in models:
        profile_id = profile.get("id", "<missing id>")
        file_name = profile.get("file")
        match = next((e for e in lock_entries if e.get("file") == file_name), None)
        if match is None:
            problem("check 9: model profile %r (%r) has no matching entry in the root lock files"
                    % (profile_id, file_name))
            continue
        if profile.get("bytes") != match.get("size"):
            problem("check 9: model profile %r bytes %r != lock size %r"
                    % (profile_id, profile.get("bytes"), match.get("size")))
        if profile.get("sha256") != match.get("sha256"):
            problem("check 9: model profile %r sha256 does not match the lock file entry" % profile_id)
        if profile.get("repo") != match.get("_repo"):
            problem("check 9: model profile %r repo %r != %s repository %r"
                    % (profile_id, profile.get("repo"), match.get("_source"), match.get("_repo")))
        if profile.get("revision") != match.get("_revision"):
            problem("check 9: model profile %r revision %r != %s revision %r"
                    % (profile_id, profile.get("revision"), match.get("_source"),
                       match.get("_revision")))

        auxiliary = profile.get("auxiliary_files")
        if auxiliary is not None:
            if profile_id != "kev":
                problem("check 9: model profile %r carries auxiliary_files; only kev declares them"
                        % profile_id)
            if not isinstance(auxiliary, list) or not auxiliary:
                problem("check 9: model profile %r auxiliary_files must be a non-empty array"
                        % profile_id)
            else:
                for aux in auxiliary:
                    aux_file = aux.get("file") if isinstance(aux, dict) else None
                    aux_match = next((e for e in lock_entries if e.get("file") == aux_file), None)
                    if aux_match is None:
                        problem("check 9: kev auxiliary file %r has no matching judge-lock.json entry"
                                % (aux_file,))
                        continue
                    if aux.get("bytes") != aux_match.get("size"):
                        problem("check 9: kev auxiliary file %r bytes %r != judge-lock size %r"
                                % (aux_file, aux.get("bytes"), aux_match.get("size")))
                    if aux.get("sha256") != aux_match.get("sha256"):
                        problem("check 9: kev auxiliary file %r sha256 does not match judge-lock.json"
                                % aux_file)
                    expected_path = "app/src/main/assets/kev/" + str(aux_file)
                    if aux.get("path") != expected_path:
                        problem("check 9: kev auxiliary file %r path %r != expected %r"
                                % (aux_file, aux.get("path"), expected_path))

    if llama_revision_text is None:
        problem("check 9: llama-revision.txt could not be read")
    else:
        declared = (identity.get("backend") or {}).get("llamaCppRevision")
        if declared != llama_revision_text.strip():
            problem("check 9: identity.backend.llamaCppRevision %r != llama-revision.txt %r"
                    % (declared, llama_revision_text.strip()))


def _iter_string_values(value, path):
    """Yield (path, string) for every string value anywhere in a JSON tree."""
    if isinstance(value, str):
        yield path, value
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from _iter_string_values(item, "%s[%d]" % (path, index))
    elif isinstance(value, dict):
        for key, item in value.items():
            yield from _iter_string_values(item, "%s.%s" % (path, key))


def check_10_defined_in_and_literal_text(manifest):
    """defined_in paths exist with a non-empty symbol; every string value
    anywhere in the manifest (including nested arrays and objects) is literal
    text: no TODO/FIXME markers, no template braces, no shell or JS template
    substitution syntax, and no <placeholder> brackets."""
    if manifest is None:
        return
    for fixture in manifest.get("fixtures", []):
        fixture_id = fixture.get("id", "<missing id>")
        defined_in = fixture.get("defined_in")
        if defined_in is not None:
            path_value = defined_in.get("path")
            symbol = defined_in.get("symbol")
            if not path_value:
                problem("check 10: fixture %s has defined_in without a path" % fixture_id)
            elif not (ROOT / path_value).is_file():
                problem("check 10: fixture %s defined_in.path does not exist: %s"
                        % (fixture_id, path_value))
            if not symbol:
                problem("check 10: fixture %s has defined_in without a non-empty symbol"
                        % fixture_id)
    forbidden_substrings = list(FORBIDDEN_LITERALS) + ["{{", "${"]
    for path, text in _iter_string_values(manifest, "manifest"):
        for literal in forbidden_substrings:
            if literal in text:
                problem("check 10: %s contains the forbidden literal %s; the manifest must "
                        "contain literal text everywhere" % (path, literal))
        match = PLACEHOLDER_PATTERN.search(text)
        if match:
            problem("check 10: %s contains an angle-bracket placeholder %r; the manifest "
                    "must contain the literal text" % (path, match.group(0)))


def _decode_java_escapes(text):
    """Decode the Java string escapes used by the manifest's quoted constants."""
    simple = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f",
              '"': '"', "\\": "\\", "'": "'"}
    out = []
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == "\\" and i + 1 < len(text):
            nxt = text[i + 1]
            if nxt in simple:
                out.append(simple[nxt])
                i += 2
                continue
            if nxt == "u" and i + 5 < len(text):
                try:
                    out.append(chr(int(text[i + 2:i + 6], 16)))
                    i += 6
                    continue
                except ValueError:
                    pass
        out.append(ch)
        i += 1
    return "".join(out)


def extract_java_string_constant(path, symbol):
    """Best-effort extraction of a simple String constant assignment
    (SYMBOL = "literal" + "literal" ... ) from a Java source file.
    Returns None when the symbol is not assigned a pure string-literal
    expression, e.g. when it is built conditionally."""
    try:
        source = path.read_text(encoding="utf-8")
    except OSError:
        return None
    match = re.search(r"\b%s\s*=\s*" % re.escape(symbol), source)
    if not match:
        return None
    i = match.end()
    parts = []
    while True:
        while i < len(source) and source[i] in " \t\r\n+":
            i += 1
        if i < len(source) and source[i] == '"':
            j = i + 1
            buf = []
            while j < len(source):
                ch = source[j]
                if ch == "\\":
                    buf.append(source[j:j + 2])
                    j += 2
                    continue
                if ch == '"':
                    break
                buf.append(ch)
                j += 1
            if j >= len(source):
                return None
            parts.append("".join(buf))
            i = j + 1
            k = i
            while k < len(source) and source[k] in " \t\r\n":
                k += 1
            if k < len(source) and source[k] == "+":
                i = k + 1
                continue
            break
        else:
            break
    if not parts:
        return None
    return _decode_java_escapes("".join(parts))


def check_10b_prompt_identity(manifest):
    """identity.prompts entries carry non-empty recorded text, and any entry
    that declares a source path and symbol is matched against the text
    extracted from that source file. When a constant cannot be extracted
    reliably (e.g. a value built conditionally), the entry must still be
    non-empty and is reported as a named warning instead of silently passing."""
    if manifest is None:
        return
    prompts = (manifest.get("identity") or {}).get("prompts")
    if not isinstance(prompts, dict) or not prompts:
        problem("check 10b: identity.prompts must be a non-empty object")
        return
    for name, entry in prompts.items():
        label = "identity.prompts.%s" % name
        if not isinstance(entry, dict):
            problem("check 10b: %s must be an object" % label)
            continue
        text = entry.get("text")
        sampled = entry.get("sampled_branch_text")
        unsampled = entry.get("unsampled_branch_text")
        has_branches = bool(sampled) and bool(unsampled)
        if not text and not has_branches:
            problem("check 10b: %s has no recorded text" % label)
            continue
        path_value = entry.get("path")
        symbol = entry.get("symbol")
        if not path_value or not symbol:
            warning("check 10b: %s declares no source path/symbol; recorded text is "
                    "unverified against source" % label)
            continue
        source_path = ROOT / path_value
        if not source_path.is_file():
            problem("check 10b: %s declares a source path that does not exist: %s"
                    % (label, path_value))
            continue
        extracted = extract_java_string_constant(source_path, symbol)
        if extracted is None:
            warning("check 10b: could not reliably extract symbol %r from %s; %s is "
                    "recorded as non-empty but unverified against source"
                    % (symbol, path_value, label))
            continue
        if text is not None and text != extracted:
            problem("check 10b: %s text does not match the extracted constant for symbol "
                    "%r in %s" % (label, symbol, path_value))
        if has_branches and sampled not in extracted and unsampled not in extracted:
            problem("check 10b: %s branch values do not match the extracted constant for "
                    "symbol %r in %s" % (label, symbol, path_value))


def check_11_gradle_identity(manifest, gradle_text):
    """Manifest application identity matches app/build.gradle (quoted or not)."""
    if manifest is None:
        return
    if gradle_text is None:
        problem("check 11: app/build.gradle could not be read")
        return
    app = (manifest.get("identity") or {}).get("app") or {}

    def gradle_int(name):
        match = re.search(r"\b%s\s+(\d+)" % re.escape(name), gradle_text)
        return int(match.group(1)) if match else None

    def gradle_string(name):
        match = re.search(r"\b%s\s+([\"'])([^\"']*)\1" % re.escape(name), gradle_text)
        return match.group(2) if match else None

    comparisons = [
        ("versionCode", app.get("versionCode"), gradle_int("versionCode")),
        ("versionName", app.get("versionName"), gradle_string("versionName")),
        ("minSdk", app.get("minSdk"), gradle_int("minSdk")),
        ("targetSdk", app.get("targetSdk"), gradle_int("targetSdk")),
        ("compileSdk", app.get("compileSdk"), gradle_int("compileSdk")),
    ]
    abi_filters = app.get("abiFilters")
    gradle_abi = gradle_string("abiFilters")
    for name, declared, found in comparisons:
        if found is None:
            problem("check 11: could not find %s in app/build.gradle" % name)
        elif declared != found:
            problem("check 11: manifest %s %r != app/build.gradle %r" % (name, declared, found))
    if gradle_abi is None:
        problem("check 11: could not find abiFilters in app/build.gradle")
    elif abi_filters != [gradle_abi]:
        problem("check 11: manifest abiFilters %r != app/build.gradle abiFilters %r"
                % (abi_filters, gradle_abi))


def check_12_observed_assessments(manifest):
    """status design: observed_0_8.assessment is either "not implemented" with a
    null evidence path, or drawn from an existing file under evidence/."""
    if manifest is None:
        return
    for fixture in manifest.get("fixtures", []):
        fixture_id = fixture.get("id", "<missing id>")
        observed = fixture.get("observed_0_8")
        if not isinstance(observed, dict):
            problem("check 12: fixture %s has no observed_0_8 object" % fixture_id)
            continue
        assessment = observed.get("assessment")
        evidence = observed.get("evidence")
        if assessment == "not implemented":
            if evidence is not None:
                problem("check 12: fixture %s assessment \"not implemented\" must carry a null "
                        "evidence path, found %r" % (fixture_id, evidence))
            if observed.get("authority") != "unmeasured":
                problem("check 12: fixture %s assessment \"not implemented\" must carry "
                        "authority \"unmeasured\"" % fixture_id)
            continue
        if not isinstance(evidence, str) or not evidence:
            problem("check 12: fixture %s has a review assessment %r without an evidence path"
                    % (fixture_id, assessment))
            continue
        evidence_path = ROOT / evidence
        if not evidence_path.is_file():
            problem("check 12: fixture %s cites evidence path that does not exist: %s"
                    % (fixture_id, evidence))
            continue
        try:
            evidence_text = evidence_path.read_text(encoding="utf-8")
        except OSError as exc:
            problem("check 12: fixture %s evidence path could not be read: %s (%s)"
                    % (fixture_id, evidence, exc))
            continue
        if assessment not in evidence_text:
            problem("check 12: fixture %s assessment %r is not drawn from %s"
                    % (fixture_id, assessment, evidence))
        if observed.get("authority") != "reviewed-outcome":
            problem("check 12: fixture %s has a review assessment but authority is not "
                    "\"reviewed-outcome\"" % fixture_id)


def check_13_coverage_matrix(manifest):
    """Coverage matrix: question family x count, evidence condition x count,
    tier counts. Unused declared families/conditions become warnings."""
    if manifest is None:
        return
    fixtures = manifest.get("fixtures", [])
    tier_counts = {}
    family_counts = {}
    condition_counts = {}
    for fixture in fixtures:
        tier_counts[fixture.get("tier")] = tier_counts.get(fixture.get("tier"), 0) + 1
        for family in fixture.get("question_families") or []:
            family_counts[family] = family_counts.get(family, 0) + 1
        for condition in fixture.get("evidence_conditions") or []:
            condition_counts[condition] = condition_counts.get(condition, 0) + 1

    MATRIX_LINES.append("tier counts: " + ", ".join(
        "%s %d" % (tier, tier_counts[tier]) for tier in sorted(tier_counts)))
    MATRIX_LINES.append("question family coverage:")
    for family in manifest.get("question_families", []):
        family_id = family.get("id")
        MATRIX_LINES.append("  %-38s %d" % (family_id, family_counts.get(family_id, 0)))
        if family_counts.get(family_id, 0) == 0:
            warning("question family declared but used by no fixture: %s" % family_id)
    MATRIX_LINES.append("evidence condition coverage:")
    for condition in manifest.get("evidence_conditions", []):
        condition_id = condition.get("id")
        MATRIX_LINES.append("  %-38s %d" % (condition_id, condition_counts.get(condition_id, 0)))
        if condition_counts.get(condition_id, 0) == 0:
            warning("evidence condition declared but used by no fixture: %s" % condition_id)


def check_network_permission_claim(manifest, android_manifest_text):
    """R-NONETWORK claims verifiable_in_current_scope "yes" because the app
    declares no INTERNET permission. If AndroidManifest.xml ever declares
    android.permission.INTERNET, that "yes" becomes an error."""
    if manifest is None:
        return
    if android_manifest_text is None:
        problem("internet cross-check: app/src/main/AndroidManifest.xml could not be read")
        return
    declares_internet = "android.permission.INTERNET" in android_manifest_text
    nonetwork = next((b for b in manifest.get("bounty_requirements", [])
                      if b.get("id") == "R-NONETWORK"), None)
    if nonetwork is None:
        problem("internet cross-check: bounty requirement R-NONETWORK is not declared")
        return
    if declares_internet and nonetwork.get("verifiable_in_current_scope") == "yes":
        problem("internet cross-check: AndroidManifest.xml declares "
                "android.permission.INTERNET, so R-NONETWORK verifiable_in_current_scope "
                "\"yes\" is false and must be corrected")


def main():
    global MANIFEST_PATH
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default="eval/fixtures-v4.json")
    args = parser.parse_args()
    MANIFEST_PATH = ROOT / args.manifest
    manifest = load_json(MANIFEST_PATH, str(MANIFEST_PATH.relative_to(ROOT)))
    if manifest and int(manifest.get("manifest_version", 0)) >= 2:
        for fixture in manifest.get("fixtures", []):
            if fixture.get("execution", {}).get("evidenceMode") not in {"retrieval", "fixed-evidence"}:
                problem("Missing/invalid explicit execution mode: " + fixture.get("id", "unknown"))
    library = load_json(LIBRARY_PATH, "app/src/androidTest/assets/library.json")
    model_lock = load_json(MODEL_LOCK_PATH, "model-lock.json")
    bonsai_lock = load_json(BONSAI_LOCK_PATH, "bonsai-lock.json")
    judge_lock = load_json(JUDGE_LOCK_PATH, "judge-lock.json")
    try:
        llama_revision_text = LLAMA_REVISION_PATH.read_text(encoding="utf-8")
    except OSError as exc:
        llama_revision_text = None
        problem("llama-revision.txt: could not be read: %s" % exc)
    try:
        gradle_text = GRADLE_PATH.read_text(encoding="utf-8")
    except OSError as exc:
        gradle_text = None
        problem("app/build.gradle: could not be read: %s" % exc)
    try:
        android_manifest_text = ANDROID_MANIFEST_PATH.read_text(encoding="utf-8")
    except OSError as exc:
        android_manifest_text = None
        problem("app/src/main/AndroidManifest.xml: could not be read: %s" % exc)

    check_01_parse_and_schema(manifest)
    check_02_top_level_keys(manifest)
    check_03_fixture_ids(manifest)
    check_04_fixture_keys(manifest)
    check_05_enum_references(manifest)
    check_06_tier_and_blockers(manifest)
    check_07_deterministic_surface(manifest)
    check_08_document_ids(manifest, library)
    check_09_identity_locks(manifest, model_lock, bonsai_lock, judge_lock, llama_revision_text)
    check_10_defined_in_and_literal_text(manifest)
    check_10b_prompt_identity(manifest)
    check_11_gradle_identity(manifest, gradle_text)
    check_12_observed_assessments(manifest)
    check_13_coverage_matrix(manifest)
    check_network_permission_claim(manifest, android_manifest_text)

    if PROBLEMS:
        print("FAILED: eval/fixtures-v4.json has %d problem(s):" % len(PROBLEMS))
        for index, message in enumerate(PROBLEMS, start=1):
            print("%3d. %s" % (index, message))
        for message in WARNINGS:
            print("warning: %s" % message)
        return 1

    fixtures = (manifest or {}).get("fixtures", [])
    tier_counts = {}
    for fixture in fixtures:
        tier_counts[fixture.get("tier")] = tier_counts.get(fixture.get("tier"), 0) + 1
    print("OK: eval/fixtures-v4.json is internally consistent with the pinned lock files "
          "and application sources.")
    print("Fixtures: %d total (%s)" % (len(fixtures), ", ".join(
        "%s: %d" % (tier, tier_counts[tier]) for tier in sorted(tier_counts))))
    print()
    print("Coverage matrix:")
    for line in MATRIX_LINES:
        print(line)
    print()
    if WARNINGS:
        print("Warnings (do not affect the exit code):")
        for message in WARNINGS:
            print("  - %s" % message)
    else:
        print("Warnings: none")
    return 0


if __name__ == "__main__":
    sys.exit(main())
