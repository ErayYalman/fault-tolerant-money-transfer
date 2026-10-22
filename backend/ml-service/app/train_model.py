from app.modeling import (
    create_model_bundle,
    default_model_path,
    save_model,
)


def main() -> None:
    model_path = default_model_path()
    bundle = create_model_bundle()

    save_model(model_path, bundle)

    print(f"Model saved to: {model_path}")
    print(f"Model version: {bundle['model_version']}")


if __name__ == "__main__":
    main()