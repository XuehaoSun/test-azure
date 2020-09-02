from typing import Dict
from json import JSONEncoder

class ChecksumSummary:
    """Interface for checksum summary."""

    def __init__(self, data: Dict[str, str]) -> None:
        self.command: str = data.get("command", "")
        self.path: str = data.get("path", "")
        self.checksum: str = data.get("checksum", "")


class ChecksumEncoder(JSONEncoder):
    def default(self, o):
        return o.__dict__