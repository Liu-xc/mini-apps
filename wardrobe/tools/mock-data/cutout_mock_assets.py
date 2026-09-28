#!/usr/bin/env python3
"""Pre-cut bundled wardrobe mock photos with the app's u2netp model.

Usage:
  python3 -m pip install Pillow numpy onnxruntime
  python3 wardrobe/tools/mock-data/cutout_mock_assets.py --destination /tmp/wardrobe-cutout

The source files are kept untouched. The output contains RGBA item-*.png files.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image

SIDE = 320
MEAN = np.asarray([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.asarray([0.229, 0.224, 0.225], dtype=np.float32)
EDGE_LOW = 0.35
EDGE_HIGH = 0.85
# item-16.png was cropped from a two-product source tile; clear the unrelated
# red garment fragment below the shirt after model segmentation.
BOTTOM_CUTS = {"item-16.png": 320}


def resize_bilinear(values: np.ndarray, out_width: int, out_height: int) -> np.ndarray:
    """Half-pixel bilinear resize matching libs/cutout's Kotlin implementation."""
    height, width = values.shape[:2]
    gy = (np.arange(out_height, dtype=np.float32) + 0.5) * height / out_height - 0.5
    gx = (np.arange(out_width, dtype=np.float32) + 0.5) * width / out_width - 0.5
    gy0 = np.floor(gy)
    gx0 = np.floor(gx)
    y0 = np.clip(gy0.astype(np.int64), 0, height - 1)
    x0 = np.clip(gx0.astype(np.int64), 0, width - 1)
    y1 = np.clip(y0 + 1, 0, height - 1)
    x1 = np.clip(x0 + 1, 0, width - 1)
    fy = np.clip(gy - gy0, 0.0, 1.0)[:, None]
    fx = np.clip(gx - gx0, 0.0, 1.0)[None, :]
    if values.ndim == 2:
        top = values[y0[:, None], x0[None, :]] * (1.0 - fx) + values[y0[:, None], x1[None, :]] * fx
        bottom = values[y1[:, None], x0[None, :]] * (1.0 - fx) + values[y1[:, None], x1[None, :]] * fx
        return top * (1.0 - fy) + bottom * fy
    top = values[y0[:, None], x0[None, :], :] * (1.0 - fx[..., None])
    top += values[y0[:, None], x1[None, :], :] * fx[..., None]
    bottom = values[y1[:, None], x0[None, :], :] * (1.0 - fx[..., None])
    bottom += values[y1[:, None], x1[None, :], :] * fx[..., None]
    return top * (1.0 - fy[..., None]) + bottom * fy[..., None]


def cutout(session: ort.InferenceSession, image: Image.Image) -> tuple[Image.Image, float]:
    rgba = np.asarray(image.convert("RGBA"), dtype=np.uint8).copy()
    height, width = rgba.shape[:2]

    rgb = resize_bilinear(rgba[:, :, :3].astype(np.float32), SIDE, SIDE) / 255.0
    tensor = ((rgb - MEAN) / STD).transpose(2, 0, 1)[None, ...].astype(np.float32)
    mask = np.asarray(session.run(None, {session.get_inputs()[0].name: tensor})[0]).squeeze()
    if mask.shape != (SIDE, SIDE):
        raise ValueError(f"unexpected mask shape {mask.shape}")

    scaled = resize_bilinear(mask.astype(np.float32), width, height)
    span = float(scaled.max() - scaled.min())
    if span <= 0.0:
        alpha = np.full((height, width), 255, dtype=np.uint8)
    else:
        normalized = np.clip((scaled - scaled.min()) / span, 0.0, 1.0)
        alpha = np.rint(np.clip((normalized - EDGE_LOW) / (EDGE_HIGH - EDGE_LOW), 0.0, 1.0) * 255.0)
        alpha = alpha.astype(np.uint8)

    rgba[:, :, 3] = alpha
    cut = BOTTOM_CUTS.get(image.filename.rsplit("/", 1)[-1]) if image.filename else None
    if cut is not None:
        rgba[cut:, :, 3] = 0
    transparent_share = float(np.count_nonzero(alpha < 255)) / alpha.size
    return Image.fromarray(rgba, mode="RGBA"), transparent_share


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source",
        type=Path,
        default=root / "tools/mock-data/wardrobe-ai-mock-20260924/images",
    )
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--model", type=Path, default=root / "app/src/main/assets/u2netp.onnx")
    args = parser.parse_args()
    args.destination.mkdir(parents=True, exist_ok=True)

    session = ort.InferenceSession(str(args.model), providers=["CPUExecutionProvider"])
    sources = sorted(args.source.glob("item-*.png"))
    if not sources:
        raise SystemExit(f"no item-*.png files found under {args.source}")
    for source in sources:
        with Image.open(source) as image:
            result, transparent_share = cutout(session, image)
            result.save(args.destination / source.name, format="PNG", optimize=True)
        print(f"{source.name}: transparent={transparent_share:.1%}")


if __name__ == "__main__":
    main()
