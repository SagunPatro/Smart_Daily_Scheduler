import re
from dataclasses import dataclass
from typing import Optional, List, Dict, Any


@dataclass
class ExtractedPreference:
    preference_type: str         # 'REPLACE_BREAK_WITH_TASK', 'ORDER_AFTER', 'PREFER_BREAK_AFTER', 'AVOID_BREAK_AFTER'
    target_activity: str         # normalized target (e.g. 'bathing', 'studying', 'coding')
    preferred_activity: Optional[str]  # normalized preferred (e.g. 'breakfast', 'lunch', 'short_break')
    relationship: str            # 'instead_of_break_after', 'after', 'avoid_after'
    rule_description: str        # e.g. 'Prefer breakfast after bathing'


# Recognized meal keywords
SUPPORTED_MEALS = {"breakfast", "lunch", "dinner"}

# Common gerund/noun mappings for normalization
STEM_MAP = {
    "bath": "bathing",
    "bathe": "bathing",
    "bathing": "bathing",
    "study": "studying",
    "studying": "studying",
    "code": "coding",
    "coding": "coding",
    "read": "reading",
    "reading": "reading",
    "run": "running",
    "running": "running",
    "work": "working",
    "working": "working",
    "walk": "walking",
    "walking": "walking",
    "exercise": "exercise",
    "workout": "workout"
}


def clean_term(term: str) -> str:
    """Cleans punctuation, articles, and 'task' suffixes from activity names."""
    if not term:
        return ""
    text = term.strip().lower()
    text = re.sub(r"[\.,!?;:'\"`]+", "", text).strip()
    # Strip leading articles
    text = re.sub(r"^(?:the|a|an)\s+", "", text).strip()
    # Strip trailing "task" or "tasks"
    text = re.sub(r"\s+tasks?$", "", text).strip()
    # Strip leading "task"
    text = re.sub(r"^tasks?\s+", "", text).strip()
    return text.strip()


def normalize_activity_name(name: str) -> str:
    """Normalizes activity name to a canonical form."""
    cleaned = clean_term(name)
    return STEM_MAP.get(cleaned, cleaned)


def activities_match(name1: str, name2: str) -> bool:
    """Checks if two activity names refer to the same task."""
    c1 = clean_term(name1)
    c2 = clean_term(name2)
    if not c1 or not c2:
        return False
    if c1 == c2:
        return True
    n1 = normalize_activity_name(c1)
    n2 = normalize_activity_name(c2)
    if n1 == n2:
        return True
    # Word boundary / substring match (e.g. 'study' matches 'study session')
    if c1 in c2 or c2 in c1:
        return True
    return False


def extract_preference_from_text(feedback_text: Optional[str]) -> Optional[ExtractedPreference]:
    """
    Rule-based preference extractor for user feedback / notes text.
    Interprets explicit preferences without using an LLM.
    Returns ExtractedPreference if recognized, or None if ambiguous/unsupported.
    """
    if not feedback_text:
        return None

    raw = feedback_text.strip().lower()
    # Remove trailing punctuation
    raw = re.sub(r"[\.,!?;]+$", "", raw).strip()
    # Collapse multiple spaces
    raw = re.sub(r"\s+", " ", raw)

    if not raw:
        return None

    # -------------------------------------------------------------
    # 1. REPLACE_BREAK_WITH_TASK
    # e.g.: "I prefer breakfast rather than a short break after bathing."
    #       "I would prefer breakfast rather than a short break after the bathing task."
    #       "prefer lunch instead of a break after studying"
    #       "prefer breakfast over break after bath"
    # -------------------------------------------------------------
    pat_replace = (
        r"^(?:i\s+)?(?:would\s+)?prefer\s+(.+?)\s+"
        r"(?:rather\s+than|instead\s+of|over)\s+"
        r"(?:a\s+)?(?:short\s+|long\s+)?\bbreak\b\s+"
        r"(?:immediately\s+)?after\s+(?:the\s+)?(?:task\s+)?(.+?)(?:\s+task)?$"
    )
    m = re.match(pat_replace, raw)
    if m:
        pref = clean_term(m.group(1))
        target = clean_term(m.group(2))
        if pref and target:
            return ExtractedPreference(
                preference_type="REPLACE_BREAK_WITH_TASK",
                target_activity=target,
                preferred_activity=pref,
                relationship="instead_of_break_after",
                rule_description=f"Prefer {pref.capitalize()} after {target}"
            )

    # -------------------------------------------------------------
    # 2. AVOID_BREAK_AFTER
    # e.g.: "Avoid placing a break immediately after bathing."
    #       "Avoid break after bathing."
    #       "No break after bathing."
    #       "Don't put a break after bathing."
    #       "Do not place a short break after bathing task."
    # -------------------------------------------------------------
    pat_avoid = (
        r"^(?:please\s+)?(?:avoid|no|don't\s+put|do\s+not\s+put|don't\s+place|do\s+not\s+place|skip)\s+"
        r"(?:placing\s+|putting\s+)?(?:a\s+)?(?:short\s+|long\s+)?\bbreak\b\s+"
        r"(?:immediately\s+)?after\s+(?:the\s+)?(?:task\s+)?(.+?)(?:\s+task)?$"
    )
    m = re.match(pat_avoid, raw)
    if m:
        target = clean_term(m.group(1))
        if target:
            return ExtractedPreference(
                preference_type="AVOID_BREAK_AFTER",
                target_activity=target,
                preferred_activity=None,
                relationship="avoid_after",
                rule_description=f"Avoid break after {target}"
            )

    # -------------------------------------------------------------
    # 3. PREFER_BREAK_AFTER
    # e.g.: "I prefer a short break after coding."
    #       "Need a break after studying."
    #       "Take a short break after coding."
    #       "Add a break after workout."
    # -------------------------------------------------------------
    pat_break_after = (
        r"^(?:i\s+)?(?:would\s+)?(?:prefer|need|want|take|add)\s+"
        r"(?:a\s+)?(?:short\s+|long\s+)?\bbreak\b\s+"
        r"(?:immediately\s+)?after\s+(?:the\s+)?(?:task\s+)?(.+?)(?:\s+task)?$"
    )
    m = re.match(pat_break_after, raw)
    if m:
        target = clean_term(m.group(1))
        if target:
            return ExtractedPreference(
                preference_type="PREFER_BREAK_AFTER",
                target_activity=target,
                preferred_activity="short_break",
                relationship="after",
                rule_description=f"Prefer a short break after {target}"
            )

    # -------------------------------------------------------------
    # 4. ORDER_AFTER
    # e.g.: "Schedule lunch after studying."
    #       "Schedule lunch after study."
    #       "Place dinner after meeting."
    #       "I prefer lunch after studying."
    # -------------------------------------------------------------
    pat_order = (
        r"^(?:please\s+)?(?:schedule|place|put|have|(?:i\s+)?(?:would\s+)?prefer)\s+"
        r"(.+?)\s+"
        r"(?:immediately\s+)?after\s+(?:the\s+)?(?:task\s+)?(.+?)(?:\s+task)?$"
    )
    m = re.match(pat_order, raw)
    if m:
        pref = clean_term(m.group(1))
        target = clean_term(m.group(2))
        if re.search(r"\bbreaks?\b", pref):
            return ExtractedPreference(
                preference_type="PREFER_BREAK_AFTER",
                target_activity=target,
                preferred_activity="short_break",
                relationship="after",
                rule_description=f"Prefer a short break after {target}"
            )
        if pref and target:
            return ExtractedPreference(
                preference_type="ORDER_AFTER",
                target_activity=target,
                preferred_activity=pref,
                relationship="after",
                rule_description=f"Schedule {pref.capitalize()} after {target}"
            )

    # If ambiguous, unsupported, or general comment, return None safely
    return None
