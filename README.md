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