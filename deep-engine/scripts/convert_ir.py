import argparse
from engine.compile import compile

parser = argparse.ArgumentParser(allow_abbrev=False)
parser.add_argument("--fp32_models", type=str, required=True)
args = parser.parse_args()

model = compile(args.fp32_models)
model.save()