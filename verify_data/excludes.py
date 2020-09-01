from typing import Any, Dict, List

class ExcludeFields:
    """Interface of exclude fields."""
    def __init__(self, data: Dict[str, List[str]]) -> None:
        fields = ["path", "name"]

        for field in fields:
            if data.get(field) is None:
                raise Exception(f"Missing required field for ExcludeFields. Please add all required fields: {fields}")

        self.name: List[str] = data.get("name")
        self.path: List[str] = data.get("path")

class Excludes:
    """Interface for an excluded directories and files in find."""

    def __init__(self, data: Dict[str, Dict[str, List[str]]]) -> None:
        """Initialize data source."""
        fields = ["local", "remote"]

        for field in fields:
            if data.get(field) is None:
                raise Exception(f"Missing required field for Exclude. Please add all required fields: {fields}")
        
        self.local: ExcludeFields = ExcludeFields(data.get("local"))
        self.remote: ExcludeFields = ExcludeFields(data.get("remote"))

    def command(self, mode: str = "local") -> str:
        """Generate command exclude arguments for find."""
        cmd = ""
        excludes = getattr(self, mode)
        for name in excludes.name:
            cmd += f"! -name \"{name}\" "

        for path in excludes.path:
            cmd += f"! -path \"{path}*\" "

        return cmd
