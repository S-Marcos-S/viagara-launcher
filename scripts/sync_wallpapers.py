#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Script to automatically curate and synchronize high-quality wallpapers from open sources
into the Viagara Launcher repository.

Generates:
- Optimized full-resolution WebP images in wallpapers/full/
- Lightweight WebP thumbnails in wallpapers/thumbnails/
- Updates wallpapers/catalog.json and app/src/main/assets/wallpapers/catalog.json
"""

import argparse
import io
import json
import os
import sys
import time
import urllib.request
import urllib.error
from typing import Dict, List, Optional, Any

try:
    from PIL import Image
    Image.MAX_IMAGE_PIXELS = None
except ImportError:
    print("Pillow (PIL) is required. Install with: pip install Pillow")
    sys.exit(1)

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG_PATH = os.path.join(BASE_DIR, "wallpapers", "catalog.json")
ASSETS_CATALOG_PATH = os.path.join(BASE_DIR, "app", "src", "main", "assets", "wallpapers", "catalog.json")
FULL_DIR = os.path.join(BASE_DIR, "wallpapers", "full")
THUMB_DIR = os.path.join(BASE_DIR, "wallpapers", "thumbnails")

GITHUB_RAW_BASE = "https://raw.githubusercontent.com/S-Marcos-S/viagara-launcher/main/wallpapers"

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

CATEGORY_QUERIES = {
    "oled": "https://wallhaven.cc/api/v1/search?categories=100&colors=000000&ratios=9x16,10x16&sorting=favorites&purity=100",
    "space": "https://wallhaven.cc/api/v1/search?categories=100&q=space&ratios=9x16,10x16&sorting=favorites&purity=100",
    "nature": "https://wallhaven.cc/api/v1/search?categories=100&q=nature&ratios=9x16,10x16&sorting=favorites&purity=100",
    "minimal": "https://wallhaven.cc/api/v1/search?categories=100&q=minimalism&ratios=9x16,10x16&sorting=favorites&purity=100",
    "abstract": "https://wallhaven.cc/api/v1/search?categories=100&q=abstract&ratios=9x16,10x16&sorting=favorites&purity=100",
    "anime": "https://wallhaven.cc/api/v1/search?categories=010&ratios=9x16,10x16&sorting=favorites&purity=100",
}


def fetch_json(url: str, timeout: int = 15) -> Optional[Dict[str, Any]]:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as e:
        print(f"Error fetching {url}: {e}")
        return None


def fetch_bytes(url: str, timeout: int = 25) -> Optional[bytes]:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return response.read()
    except Exception as e:
        print(f"Error downloading {url}: {e}")
        return None


def get_dominant_color_hex(img: Image.Image) -> str:
    small = img.copy().resize((1, 1), Image.Resampling.BOX)
    r, g, b = small.getpixel((0, 0))[:3]
    return f"#{r:02X}{g:02X}{b:02X}"


def clean_name(raw_name: str) -> str:
    cleaned = raw_name.replace("-", " ").replace("_", " ").strip()
    return " ".join(w.capitalize() for w in cleaned.split() if w)


def process_and_save_wallpaper(
    item_id: str,
    image_bytes: bytes,
    full_path: str,
    thumb_path: str,
) -> bool:
    try:
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        w, h = img.size

        # Resize full image if excessively large to keep storage efficient
        max_h = 2560
        if h > max_h:
            new_w = int(w * (max_h / h))
            full_img = img.resize((new_w, max_h), Image.Resampling.LANCZOS)
        else:
            full_img = img

        full_img.save(full_path, format="WEBP", quality=85, method=4)

        # Generate thumbnail (target height 720)
        thumb_h = 720
        thumb_w = int(w * (thumb_h / h))
        thumb_img = img.resize((thumb_w, thumb_h), Image.Resampling.LANCZOS)
        thumb_img.save(thumb_path, format="WEBP", quality=75, method=4)

        return True
    except Exception as e:
        print(f"Failed to process image for {item_id}: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description="Sync curated wallpapers into repository.")
    parser.add_argument("--max-per-category", type=int, default=1, help="Max new items per category per run")
    parser.add_argument("--max-total", type=int, default=3, help="Max total new items added per run")
    parser.add_argument("--categories", type=str, default="oled,space,nature,minimal,abstract", help="Comma-separated categories")
    parser.add_argument("--dry-run", action="store_true", help="Inspect without modifying files")
    args = parser.parse_args()

    os.makedirs(FULL_DIR, exist_ok=True)
    os.makedirs(THUMB_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(ASSETS_CATALOG_PATH), exist_ok=True)

    if not os.path.exists(CATALOG_PATH):
        catalog = {
            "version": 1,
            "categories": [
                {"id": "all", "label": "Todos"},
                {"id": "oled", "label": "OLED"},
                {"id": "abstract", "label": "Abstrato"},
                {"id": "space", "label": "Espaço"},
                {"id": "minimal", "label": "Minimalista"},
                {"id": "nature", "label": "Natureza"},
            ],
            "wallpapers": [],
        }
    else:
        with open(CATALOG_PATH, "r", encoding="utf-8") as f:
            catalog = json.load(f)

    existing_wallpapers = catalog.get("wallpapers", [])
    existing_ids = {w["id"] for w in existing_wallpapers}
    existing_urls = {w["full_url"] for w in existing_wallpapers}

    target_categories = [c.strip() for c in args.categories.split(",") if c.strip() in CATEGORY_QUERIES]
    added_count = 0
    new_items: List[Dict[str, Any]] = []

    print(f"Starting wallpaper sync (max total: {args.max_total}, max per category: {args.max_per_category})...")

    for cat in target_categories:
        if added_count >= args.max_total:
            break

        query_url = CATEGORY_QUERIES[cat]
        print(f"\nQuerying category '{cat}'...")
        res = fetch_json(query_url)
        if not res or "data" not in res:
            print(f"No results for category '{cat}'")
            continue

        cat_added = 0
        for item in res["data"]:
            if added_count >= args.max_total or cat_added >= args.max_per_category:
                break

            wh_id = item["id"]
            wallpaper_id = f"wallpaper_wh_{wh_id}"
            if wallpaper_id in existing_ids:
                continue

            dim_x = item.get("dimension_x", 0)
            dim_y = item.get("dimension_y", 0)
            if dim_y <= dim_x or dim_y < 1600:
                continue

            image_url = item.get("path")
            if not image_url or image_url in existing_urls:
                continue

            print(f"Found candidate: {wh_id} ({dim_x}x{dim_y}) for category '{cat}'")

            # Fetch extra details for title and tags if possible
            time.sleep(1.0)
            detail = fetch_json(f"https://wallhaven.cc/api/v1/w/{wh_id}")
            tags = [cat]
            author = "Wallhaven"
            title = None

            disallowed_tags = {
                "vertical", "portrait display", "simple background", "no people",
                "women", "girl", "girls", "men", "boy", "model", "brunette", "blonde",
                "long hair", "short hair", "looking at viewer", "asian", "white hair",
                "black hair", "anime", "anime girls", "drawn", "digital art", "artwork",
                "wallpaper", "photoshop", "picture", "minimalism", "abstract", "nature", "space", "oled"
            }

            if detail and "data" in detail:
                d = detail["data"]
                uploader = d.get("uploader", {}).get("username")
                if uploader:
                    author = uploader
                d_tags = [t["name"] for t in d.get("tags", []) if t.get("name")]
                if any(t.lower() in ["error", "errors", "glitch", "broken", "404"] for t in d_tags):
                    print(f"Skipping candidate {wh_id} due to error/glitch tags.")
                    continue
                filtered_tags = [t for t in d_tags if t.lower() not in disallowed_tags]
                if filtered_tags:
                    title = clean_name(filtered_tags[0])
                    tags.extend(filtered_tags[:3])

            if not title:
                title = f"{cat.capitalize()} #{wh_id.upper()}"

            colors = item.get("colors", [])
            primary_color = colors[0] if colors else "#000000"

            full_filename = f"{wallpaper_id}.webp"
            thumb_filename = f"{wallpaper_id}.webp"
            full_dest = os.path.join(FULL_DIR, full_filename)
            thumb_dest = os.path.join(THUMB_DIR, thumb_filename)

            if args.dry_run:
                print(f"[DRY-RUN] Would download {image_url} -> {full_dest} ({title} by {author})")
                cat_added += 1
                added_count += 1
                continue

            print(f"Downloading {image_url}...")
            img_bytes = fetch_bytes(image_url)
            if not img_bytes:
                print(f"Failed to download image for {wh_id}, skipping.")
                continue

            print(f"Processing and converting to WebP...")
            success = process_and_save_wallpaper(wallpaper_id, img_bytes, full_dest, thumb_dest)
            if not success:
                continue

            new_entry = {
                "id": wallpaper_id,
                "name": title,
                "author": author,
                "thumbnail_url": f"{GITHUB_RAW_BASE}/thumbnails/{thumb_filename}",
                "full_url": f"{GITHUB_RAW_BASE}/full/{full_filename}",
                "primary_color": primary_color,
                "category": cat,
                "tags": list(dict.fromkeys(tags)),
            }

            existing_ids.add(wallpaper_id)
            existing_urls.add(image_url)
            new_items.append(new_entry)
            cat_added += 1
            added_count += 1
            print(f"Successfully added '{title}' (id: {wallpaper_id}) to category '{cat}'")

    if args.dry_run:
        print(f"\n[DRY-RUN] Finished. Would have added {added_count} wallpapers.")
        return

    if not new_items:
        print("\nNo new wallpapers were added.")
        return

    # Append new items to existing catalog
    catalog["wallpapers"].extend(new_items)

    # Save to wallpapers/catalog.json
    with open(CATALOG_PATH, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write("\n")

    # Sync to assets catalog
    with open(ASSETS_CATALOG_PATH, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"\nSuccessfully added {len(new_items)} new wallpapers and updated catalog files.")
    for item in new_items:
        print(f"- [{item['category']}] {item['name']} by {item['author']} ({item['id']})")


if __name__ == "__main__":
    main()
