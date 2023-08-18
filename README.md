- Step 2: Enable pruning functionalities 

     \[**Experimental option** \]Modify model and optimizer.


### Task request description

- `script_url` (str): The URL to download the model archive.
- `optimized` (bool): If `True`, the model script has already be optimized by `Neural Coder`.
- `arguments` (List\[Union\[int, str\]\], optional): Arguments that are needed for running the model.
- `approach` (str, optional): The optimization approach supported by `Neural Coder`.
- `requirements` (List\[str\], optional): The environment requirements.
- `priority`(int, optional): The importance of the task, the optional value is `1`, `2`, and `3`, `1` is the highest priority. <!--- Can not represent how many workers to use. -->

## Design Doc for Optimization as a Service \[WIP\]

# Security Policy

## Report a Vulnerability

Please report security issues or vulnerabilities to the [Intel® Security Center].

For more information on how Intel® works to resolve security issues, see
[Vulnerability Handling Guidelines].

[intel® security center]: https://www.intel.com/security
[vulnerability handling guidelines]: https://www.intel.com/content/www/us/en/security-center/vulnerability-handling-guidelines.html


Model inference: Roughly speaking , two key steps are required to get the model's result. The first one is moving the model from the memory to the cache piece by piece, in which, memory bandwidth $B$ and parameter count $P$ are the key factors, theoretically the time cost is  $P\*4 /B$. The second one is  computation, in which, the device's computation capacity  $C$  measured in FLOPS and the forward FLOPs $F$ play the key roles, theoretically the cost is $F/C$.