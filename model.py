from dataclasses import dataclass
from typing import List, Optional, Tuple

@dataclass
class Task:
    name: str
    duration: int  # in minutes
    preferred_window: Optional[Tuple[int, int]] = None


@dataclass
class TimeBlock:
    start: int
    end: int
    label: str


@dataclass
class Schedule:
    blocks: List[TimeBlock]

