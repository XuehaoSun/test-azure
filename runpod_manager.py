import requests
import argparse
import sys
import json
import time


def run_graphql_query(api_key, payload):
    url = f"https://api.runpod.io/graphql?api_key={api_key}"
    headers = {"Content-Type": "application/json"}
    response = requests.post(url, json={"query": payload}, headers=headers)
    response.raise_for_status()
    if response.status_code != 200:
        print(f"❌ HTTP Error: {response.status_code}")
        print(response.text)
        sys.exit(1)

    result = response.json()
    if "errors" in result:
        print("❌ Errors:")
        print(json.dumps(result["errors"], indent=2))
        sys.exit(1)
    return result


def create_pod(args):
    env_list = []
    if args.env:
        for e in args.env:
            if "=" in e:
                key, value = e.split("=", 1)
                env_list.append({"key": key, "value": value})

    payload = {
        "name": args.name,
        "imageName": args.image,
        "gpuTypeIds": [args.gpu_type],
        "cloudType": "SECURE",
        "gpuCount": args.gpu_count,
        "containerDiskInGb": args.container_disk_size,
        "env": env_list,
    }

    payload = """
    mutation {{
      podFindAndDeployOnDemand(input: {{
        cloudType: SECURE,
        gpuCount: {gpu_count},
        volumeInGb: 0,
        containerDiskInGb: {container_disk_size},
        gpuTypeId: "{gpu_type}",
        name: "{name}",
        imageName: "{image}",
        dockerArgs: "{docker_args}"
      }}) {{
        id
      }}
    }}
    """.format(
        gpu_type=args.gpu_type,
        name=args.name,
        image=args.image,
        docker_args=args.env,
        container_disk_size=args.container_disk_size,
        gpu_count=args.gpu_count,
    )

    print(f"🚀 Creating pod: {args.name}...")
    data = run_graphql_query(args.api_key, payload)
    if data:
        pod_id = data.get("id")
        if pod_id:
            print(f"✅ Pod created successfully! Pod ID: {pod_id}")
            print(f"    Status is: {data.get('desiredStatus')}")
    else:
        print("❌ Failed to create pod (no data returned).")
        sys.exit(1)


def get_pod_id(args):
    url = f"https://rest.runpod.io/v1/pods?name={args.name}"
    headers = {"Authorization": "Bearer " + args.api_key}
    try:
        response = requests.get(url, headers=headers)
        response.raise_for_status()
        data = response.json()[0] if response.json() else None
        if data:
            print(f"Pod status: {data.get('desiredStatus', 'unknown')}")
        if data and "id" in data:
            return data["id"]

        print(f"⚠️ Pod '{args.name}' not found.")
        return None

    except Exception as e:
        print(f"⚠️ Error fetching pods: {e}")
        raise e


def wait_for_pod(args):
    for _ in range(60):  # Wait up to 10 minutes
        pod_id = get_pod_id(args)
        if pod_id:
            print(f"✅ Pod '{args.name}' is now available with ID: {pod_id}")
            return
        else:
            print(f"⏳ Waiting for pod '{args.name}' to be created...")
            time.sleep(10)
    print(f"❌ Timeout: Pod '{args.name}' was not created within the expected time.")
    sys.exit(1)


def terminate_pod(args):
    pod_id = args.pod_id or get_pod_id(args)
    if not pod_id:
        get_pod_id(args)  # Just to check if pod exists and print status
        sys.exit(1)

    url = f"https://api.runpod.io/graphql?api_key={args.api_key}"
    query = f"""
    mutation {{
      podTerminate(input: {{ podId: "{pod_id}" }})
    }}
    """
    response = requests.post(url, json={"query": query}, timeout=10)
    response.raise_for_status()

    max_tries = 30

    for i in range(max_tries):  # Wait up to 5 minutes for termination
        pod_id = get_pod_id(args)
        if pod_id:
            print(f"⚠️ Pod {args.name}: {pod_id} termination initiated, but pod still exists")
            if i >= max_tries - 1:
                raise Exception(
                    f"❌ Pod {args.name}: {pod_id} termination may not have completed yet. Please check the status."
                )
        else:
            print(f"✅ Pod {args.name} termination command sent.")
            break
        time.sleep(10)  # Wait a bit for termination to process


def main():
    parser = argparse.ArgumentParser(description="RunPod Pod Manager via API")
    parser.add_argument("--action", choices=["create", "terminate", "wait"], required=True)
    parser.add_argument("--api_key", required=True)
    parser.add_argument("--pod_id", help="Pod ID for termination")
    parser.add_argument("--name", help="Pod name")
    parser.add_argument("--gpu_type", help="GPU type ID")
    parser.add_argument("--image", help="Container image")
    parser.add_argument("--gpu_count", type=int, default=1)
    parser.add_argument("--container_disk_size", type=int, default=50)
    parser.add_argument("--env", nargs="*", help="Environment variables in KEY=VALUE format")

    args = parser.parse_args()

    if args.action == "create":
        create_pod(args)
    elif args.action == "terminate":
        terminate_pod(args)
    elif args.action == "wait":
        wait_for_pod(args)


if __name__ == "__main__":
    main()
