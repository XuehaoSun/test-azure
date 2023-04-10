# SparseLib CI

Trigger with PR comment `test pr-sparse`.

## Optional PR comment parameters
Usage:
- `test pr-sparse`
- `test pr-sparse: [options]`

Options:
| Option | Description |
|--------|-------------|
| `--ut`                            | Run UT (sparselib and engine)
| `--ut=sparse_only`                | Run sparselib UT only
| `--benchmark`                     | Run sparselib benchmark
| `--cpplint`                       | Run cpplint check
| `--bandit`                        | Run bendit check
| `--spell`                         | Run speel check
| `--copyright`                     | Run copyright check
| `--windows`                       | Run test on Windows
| `--gpu`                           | Run test on GPU
| `--models`                        | Run all neural engine models containing sparselib kernels 
| `--models=<models>`               | Run a specified list (comma separated) of models with neural engine
| `--inferencer_config=<configs>`   | Specify a list (comma separated) of model running configs in the format of `<ncores_per_instance>:<bs>` 
| `--node=<node_label>`             | Specify computing node labels for tasks (except ut) to run; e.g. `ILIT&&non-perf-sdp`
| `--node_ut=<node_label>`          | Specify computing node labels for ut task(s) to run; e.g. `ILIT&&non-perf-sdp`
| `--node_windows=<node_label>`     | Specify computing node labels for windows task(s) to run; e.g. `ILIT&&windows`

If no parameters are presented, the project parameters set in Jenkins will be used.
