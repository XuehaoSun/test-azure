import subprocess
from typing import Dict, Union


class Source:
    """Interface for Dataset source."""
    def __init__(self, data: Dict[str, Union[str, bool]]) -> None:
        """Initialize data source."""
        fields = ["link", "direct", "download"]

        for field in fields:
            if data.get(field) is None:
                raise Exception(f"Missing required field. Please add all required fields: {fields}")
        
        self.link: str = data.get("link")
        self.direct: bool = data.get("direct")
        self.download: bool = data.get("download")
    
    def summary(self) -> str:
        """Get source summary."""
        return f"\tlink: {self.link}\n" \
               f"\tdirect: {self.direct}\n" \
               f"\tdownload: {self.download}\n"
    
    def get_source(self, workdir, test = False) -> bool:
        """Download sources."""
        downloaded = False
        if self.download and self.direct:
            cmd = ["wget", self.link]
            if test:
                print(f"[ TEST ] Executing {cmd}")
                return True
            wget_proc = subprocess.run(cmd, cwd=workdir)
            if wget_proc.returncode != 0:
                raise Exception(f"Could not download source from {self.link}")
            downloaded = True
        return downloaded
