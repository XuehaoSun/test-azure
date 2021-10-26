import argparse
import subprocess
from threading import Lock, Thread
from typing import Any, Dict, List, Optional
from utils.utils import get_number_of_sockets
import platform
import os

import psutil

LOCK = Lock()
total_cores = psutil.cpu_count(logical=False)
total_sockets = get_number_of_sockets()

cores_per_socket = total_cores // total_sockets


def validate_parameters(instances, sockets):
    if sockets > total_sockets:
        raise Exception(f"Not enough sockets. This platform has {total_sockets} socket(s).")

    if total_cores % instances != 0:
        raise Exception("Could not divide cores per instance.")

    if instances % sockets != 0:
        raise Exception("Could not divide instances per sockets.")


def execute_multi_instance(cmd: List[str],
                           instances: int,
                           sockets: int,
                           output_prefix: str,
                           cwd: Optional[str] = None,
                           use_ht=False,
                           shell=True,
                           env: Dict[str, str] = None):
    validate_parameters(instances, sockets)
    commands = collect_commands(cmd, instances, sockets, use_ht)
    threads = []
    for idx, cmd in enumerate(commands):
        threads.append(Thread(target=call_one,
                              args=(cmd, cwd, f"{output_prefix}_{idx}.log", shell, env)))
    # Start all threads
    for command_thread in threads:
        command_thread.start()

    # Wait for all of them to finish
    for command_thread in threads:
        command_thread.join()


def call_one(args: List[Any],
             cwd: str,
             filename: str,
             shell: bool = False,
             env: Optional[Dict[str,str]] = None,) -> None:

    try:
        run(args, cwd, filename, shell, env)
    except Exception:
        raise Exception(f"Unexpected error for command line {args}")
    try:
        LOCK.acquire()
    finally:
        LOCK.release()


def run(args: List[Any],
        cwd: str,
        filename: str,
        shell: bool = False,
        env: Optional[Dict[str, str]] = None) -> subprocess.Popen:
    cmd = " ".join(map(str, args)) if shell else map(str, args)
    env_variables = os.environ.copy()  # Inherit environment variables from host
    if env:
        env_variables.update(env)
    proc = subprocess.Popen(cmd,
                            cwd=cwd,
                            env=env_variables,
                            stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT,
                            shell=shell)
    with open(filename, "w", 1, encoding="utf-8") as log_file:
        log_file.write(f"[ COMMAND ] {cmd} \n")
        print(f"[ COMMAND ] {cmd} \n")
        for line in proc.stdout:
            decoded_line = line.decode("utf-8", errors="ignore").strip()
            log_file.write(decoded_line + "\n")
            print(decoded_line)

    proc.wait()
    return_code = proc.returncode
    msg = "Exit code: {}".format(return_code)
    if return_code != 0:
        raise Exception(msg)
    print(msg)

    return proc


def collect_commands(cmd, instances, sockets, use_ht = False):
    available_cores = cores_per_socket * sockets
    cores_per_instance = available_cores // instances

    commands: List[List[str]] = []
    for instance in range(instances):
        system = platform.system()
        core_binding = []

        if system == "Linux":
            core_binding = get_numa(instance, cores_per_instance, use_ht)
        elif system == "Windows":
            core_binding = get_win_affinity(instance, cores_per_instance, use_ht)
        command = core_binding + cmd

        commands.append(command)

    return commands


def get_numa(instance: int, cores_per_instance: int, use_ht = False):
    start_core = instance * cores_per_instance
    end_core = start_core + cores_per_instance -1
    socket = start_core // cores_per_socket
    numa = f"numactl --membind={socket} --physcpubind={start_core}-{end_core}"

    if use_ht:
        start_ht_core = start_core + total_cores
        end_ht_core = end_core + total_cores
        numa += f",{start_ht_core}-{end_ht_core}"
    return [numa]

def get_win_affinity(instance: int, cores_per_instance: int, use_ht = False):
    total_cores = psutil.cpu_count(logical=False)
    total_sockets = get_number_of_sockets()
    ncores_per_socket = total_cores // total_sockets

    base_core = int('1'*cores_per_instance, 2)

    instance_cores = base_core << instance*cores_per_instance
    if use_ht:
        instance_cores = (instance_cores<<ncores_per_socket) + instance_cores

    socket = (instance * cores_per_instance) // ncores_per_socket
    
    return [
        "start",
        "/b",
        "/WAIT",
        "/node", f"{socket}",
        "/affinity", f"{hex(instance_cores)[2:]}",
        "CMD", "/c"
        ]
