import os
import subprocess
from typing import Any, Dict, List, Optional

import utils as utils
from excludes import Excludes
from source import Source


class Model:
    """Interface for model."""
    def __init__(self, data: Dict[str, Any]) -> None:
        """Initialize Model class."""
        fields = [
            "name",
            "sources",
            "execution",
            "topologies",
            "built-in",
            "local_path",
            "remote_path",
            "exclude",
            "skip",
            "envs"
        ]
        
        for field in fields:
            if data.get(field) is None:
                raise Exception(f"Missing required field. Please add all required fields: {fields}")

        self.name:str = data.get("name")
        self.sources: List[Source] = [ Source(x) for x in data.get("sources") ]
        self.execution: List[str] = data.get("execution")
        self.topologies: List[str] = data.get("topologies")
        self.built_in: bool = data.get("built-in")
        self.local_path: str = data.get("local_path")
        self.remote_path: str = data.get("remote_path")
        self.exclude: Excludes = Excludes(data.get("exclude"))
        self.skip: bool = data.get("skip")
        self.envs: Dict[str, str] = data.get("envs")
        self.datadir: Optional[str] = None


    def summary(self) -> str:
        """Print model summary."""
        return f"name: {self.name}\n" \
               f"sources:\n{self.sources_summary()}\n" \
               f"execution: {self.execution_command}\n" \
               f"topologies: {self.topologies}\n" \
               f"built-in: {self.built_in}\n" \
               f"skip: {self.skip}"


    def sources_summary(self) -> str:
        """Get sources summary."""
        summary = ""
        for source in self.sources:
            summary += source.summary()
        return summary


    @property
    def execution_command(self) -> str:
        """Get execution command."""
        return " && ".join(self.execution)


    def execute(self, topology: Optional[str], test = False) -> None:
        """Execute model preprocessing command."""
        cmd = self.execution_command
        for env, val in self.envs.items():
            cmd = cmd.replace(f"%%{env}%%", val)
        
        cmd = self.update_topology(cmd, topology)

        if test:
            print(f"[ TEST ] Executing: \"{cmd}\"")
            return

        print(f"[ INFO ] Executing: \"{cmd}\"")
        proc = subprocess.run(cmd, cwd=self.datadir, shell=True)
        if proc.returncode != 0:
            raise Exception("Error while preprocessing model!")


    def md5_command(self, mode: str, topology: Optional[str], test = False) -> Optional[Dict[str, str]]:
        """Get md5sum calculation execution command."""
        command = None

        local_resource_path = os.path.join(self.datadir, self.local_path)
        local_resource_path = self.update_topology(local_resource_path, topology)

        resource_mapping = {
            "local": local_resource_path,
            "remote": self.remote_path
        }

        resource_path = resource_mapping.get(mode, None)
        if resource_path is None:
            raise Exception(f"Not supported mode: {mode}")

        resource_path = self.update_topology(resource_path, topology)

        resource_type = None
        if os.path.isfile(resource_path):
            resource_type = "file"
        elif os.path.isdir(resource_path):
            resource_type = "dir"
        
        if test:
            resource_type = "file" if "." in os.path.basename(resource_path) else "dir"

        if resource_type == "file":
            command = {
                "cmd": f"md5sum {resource_path}",
                "cwd": os.path.dirname(resource_path)
            }
        elif resource_type == "dir":
            parrent_dir, dirname = os.path.split(resource_path)
            
            command = {
                "cmd": "find " + dirname + " -type f " + self.exclude.command(mode) + " -exec md5sum {} \; | sort -k 2 | md5sum",
                "cwd": parrent_dir
            }
        return command


    def calculate_md5(self, mode: str, topology: Optional[str], test = False) -> Dict[str, str]:
        """Calculate MD5 checksum."""
        command = self.md5_command(mode, topology, test)
        if command is None:
            raise Exception("[ ERROR ] Could not set command for md5 calculation.")
        try:
            cmd = command.get("cmd")
            cwd = command.get("cwd")

            resource_path = self.remote_path if mode == "remote" else os.path.join(self.datadir, self.local_path)
            resource_path = self.update_topology(resource_path, topology)
            cmd = self.update_topology(cmd, topology)

            print(f"[ INFO ] Getting checksum for {resource_path}")

            if test:
                print(f"[ TEST ] Working directory: {cwd}")
                print(f"[ TEST ] Executing: {cmd}")

                return {
                    "command": cmd,
                    "path": resource_path,
                    "checksum": "test"
                }
            
            print(f"[ INFO ] Getting checksum for {resource_path}")
            print(f"[ INFO ] Executing: {cmd}")
            proc = subprocess.run(cmd, cwd=cwd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            output = proc.stdout
            checksum = output.decode().split(" ")[0].strip()
            return {
                "command": cmd,
                "path": resource_path,
                "checksum": checksum
            }
        except Exception as err:
            raise Exception(f"Could not calculate checksum: {err}")


    @staticmethod
    def update_topology(line: str, topology: Optional[str]) -> str:
        """Replaces %%_topology%% to a specified topology."""
        if topology:
            return line.replace(r"%%_topology%%", topology)
        return line

    def update_local_path(self, framework: str, group: str, topology: str) -> None:
        """Try to update local path."""
        mapped_path = utils.map_local_directory(framework, group, topology)
        if mapped_path is not None:
            self.local_path = mapped_path

    def update_remote_path(self, framework: str, group: str, topology: str) -> None:
        """Try to update remote path."""
        mapped_path = utils.map_remote_directory(framework, group, topology)
        if mapped_path:
            self.remote_path = mapped_path
